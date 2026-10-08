package com.ruoyi.system.agent.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.system.agent.domain.EvidenceCard;
import com.ruoyi.system.agent.domain.IndicatorSummary;
import com.ruoyi.system.agent.domain.StockDataContext;
import com.ruoyi.system.agent.util.PromptUtils;
import com.ruoyi.system.chan2.Chan2Engine;
import com.ruoyi.system.chan2.Chan2Kline;
import com.ruoyi.system.chan2.Chan2Result;
import com.ruoyi.system.domain.StockFinancialIndexDTO;
import com.ruoyi.system.domain.StockKlineDTO;
import com.ruoyi.system.domain.StockRealTimePriceDTO;
import com.ruoyi.system.service.DeepSeekService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 四个子 Agent：缠论 / 技术指标 / 经典形态 / 基本面舆情。
 *
 * <p>所有子 Agent 输出标准化"证据卡"（客观事实，不含主观多空结论），
 * 供后续多空辩论与法官裁决引用。大模型与 Key 复用 {@link DeepSeekService}。</p>
 */
@Slf4j
@Service
public class SubAgentService {

    public static final String AGENT_CHAN = "缠论";
    public static final String AGENT_TECH = "技术";
    public static final String AGENT_PATTERN = "形态";
    public static final String AGENT_FUND = "基本面";

    private static final String CARD_FORMAT = "{\"summary\":\"一句话客观概述\",\"evidence\":"
            + "[{\"item\":\"证据标题\",\"detail\":\"数据细节\",\"strength\":\"强|中|弱\",\"confidence\":90,"
            + "\"direction\":\"多|空|中性\",\"source\":\"出处，如：日线/60分钟/财报/舆情\"}]}"
            + "\nconfidence为0-100的整数置信度：数值越高表示你对该条证据事实成立的确信程度越高；数据不足或判断依据较弱时请给低分(如40-60)，依据明确可靠时给高分(如80-95)。"
            + "\ndirection为该证据的客观信号方向：利好/看涨的技术信号填\"多\"，利空/看跌的技术信号填\"空\"，无方向或中性填\"中性\"。direction只是信号偏向的客观标注，不是买卖建议。";

    /** 缠论证据置信度折损系数：因缠论代码分析不够准确，整体额外打7.5折 */
    private static final double CHAN_CONFIDENCE_FACTOR = 0.75;

    @Autowired
    private DeepSeekService deepSeekService;

    /** 经典形态知识库文本（resources/agent/patterns.json） */
    private String patternLibText;

    @PostConstruct
    void loadPatternLibrary() {
        try (InputStream in = getClass().getResourceAsStream("/agent/patterns.json")) {
            patternLibText = in == null ? "[]" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            log.info("经典形态知识库加载完成，共{}个形态", JSONArray.parseArray(patternLibText).size());
        } catch (Exception e) {
            log.warn("经典形态知识库加载失败，形态Agent将无知识库辅助: {}", e.getMessage());
            patternLibText = "[]";
        }
    }

    // ==================== 缠论 Agent ====================

    /**
     * 短线监控模式的提示前缀：强调本次为短线实时交易，只关注短期走势（15分钟+日线）。
     * 仅在 monitor 监控定时任务开启，页面全量分析不触发，不影响其它功能。
     */
    private String shortTermHint(StockDataContext ctx) {
        if (ctx.isShortTermMonitor()) {
            return "\n【重要交易背景】本次为短线实时交易，作为方法调用模式（快速/短线），"
                    + "请您只关注 15 分钟与日线级别的短期走势，忽略中长期/长期趋势，"
                    + "证据与结论应基于短期买卖点判断。\n";
        }
        return "";
    }

    /**
     * 缠论中枢理论分析：先本地跑缠论引擎得到结构事实，再由 LLM 依据缠论规则解读为证据卡。
     */
    public EvidenceCard chanAgent(StockDataContext ctx) {
        String system = "你是资深缠论分析师。任务：基于程序计算出的缠论结构事实（分型/笔/线段/中枢/背驰），"
                + "判断当前处于缠论框架下的什么位置。\n"
                + "缠论要点提醒：\n"
                + "1. 第一类买点=下跌趋势中最后一个中枢之后的底背驰；第一类卖点=上涨趋势中最后一个中枢之后的顶背驰。\n"
                + "2. 第二类买点=一买后次级别回调不创新低的低点；第二类卖点=一卖后次级别反弹不创新高的高点。\n"
                + "3. 第三类买点=向上离开中枢后次级别回调不回到中枢（ZD上方）的点；第三类卖点=向下离开中枢后次级别反弹不回到中枢（ZG下方）的点。\n"
                + "4. 背驰=同向趋势中价格创新高/新低而MACD的DIF峰值或柱面积未创新高/新低。\n"
                + "5. 中枢震荡=价格在中枢区间[ZD,ZG]内反复；三买三卖与中枢突破相关。\n"
                + "输出要求：只输出JSON，格式：" + CARD_FORMAT + "\n"
                + "严禁：给出看多/看涨/看空/看跌/买入/卖出等主观结论或建议；每条证据必须注明级别（如日线/60分钟）。"
                + "可以客观描述\"疑似第X类买卖点\"\"顶背驰\"\"中枢震荡\"等结构性事实。";
        String user = shortTermHint(ctx) + "股票：" + ctx.getStockName() + "(" + ctx.getStockCode() + ") 操作类型：" + ctx.getOperateTypeName()
                + "\n程序计算的缠论结构事实如下：\n" + buildChanFacts(ctx)
                + "\n请依据以上结构事实输出证据卡JSON。";
        return parseCard(AGENT_CHAN, deepSeekService.chat(system, PromptUtils.limit(user, 12000)));
    }

    // ==================== 技术指标 Agent ====================

    /**
     * 技术指标分析：KDJ/MACD/BOLL/均线/量能数值摘要 + 大盘指数环境 → 证据卡。
     */
    public EvidenceCard techAgent(StockDataContext ctx) {
        String system = "你是资深技术分析师。基于 KDJ、MACD、BOLL、均线、量能的数值摘要与大盘指数环境，输出客观证据卡。\n"
                + "关注：KDJ超买(>80)超卖(<20)与金叉死叉、MACD金叉死叉与DIF位置、"
                + "均线多头/空头排列与粘合、BOLL轨道压力支撑与收口开口、量能放大萎缩、"
                + "股价相对均线位置、多级别共振情况、涨跌停交易行为特征。\n"
                + "输出要求：只输出JSON，格式：" + CARD_FORMAT + "\n"
                + "严禁给出看多/看空/买卖等主观结论，只陈述指标客观状态。";
        StringBuilder user = new StringBuilder();
        user.append(shortTermHint(ctx));
        user.append("股票：").append(ctx.getStockName()).append("(").append(ctx.getStockCode())
                .append(") 市场属：").append(ctx.getMarket())
                .append(" 涨跌停幅度:").append(ctx.getLimitPct()).append("%")
                .append(" 操作类型：").append(ctx.getOperateTypeName()).append('\n');
        user.append("\n【各级别技术指标摘要】\n");
        for (Map.Entry<String, IndicatorSummary> e : ctx.getIndicators().entrySet()) {
            user.append(indicatorLine(e.getValue())).append('\n');
        }
        user.append("\n【大盘指数环境】\n");
        ctx.getIndexSummaries().forEach((k, v) -> user.append(v).append('\n'));
        StockDataContext.LimitUpStats lus = ctx.getLimitUpStats();
        user.append("\n【涨跌停表现】近120日涨停").append(lus.getLimitUpCount120())
                .append("次/跌停").append(lus.getLimitDownCount120())
                .append("次，近20日涨停").append(lus.getLimitUpCount20())
                .append("次，最近涨停日:").append(lus.getLastLimitUpDate() == null ? "无" : lus.getLastLimitUpDate())
                .append("，当前连板:").append(lus.getCurrentStreak())
                .append("，今日状态:").append(lus.getTodayStatus()).append('\n');
        user.append("\n请输出证据卡JSON。");
        return parseCard(AGENT_TECH, deepSeekService.chat(system, user.toString()));
    }

    // ==================== 形态 Agent ====================

    /**
     * 经典形态分析：形态知识库 + 日线K线序列 → 证据卡（双顶/双底/老鸭头等，非缠论）。
     */
    public EvidenceCard patternAgent(StockDataContext ctx) {
        String system = "你是擅长经典K线形态识别的资深交易员。依据下方\"经典形态知识库\"中的形态定义，"
                + "对照日线K线序列判断当前走势是否构成（或疑似构成）某种形态。\n"
                + "规则：1.必须严格对照K线数据判断，不得凭空想象；2.可以输出多个疑似形态；"
                + "3.没有匹配形态时如实输出\"未识别出经典形态\"；"
                + "4.形态的\"implication\"是知识库中的客观描述，可以作为证据的 detail 引用。\n"
                + "输出要求：只输出JSON，格式：" + CARD_FORMAT + "\n"
                + "严禁给出看多/看空/买卖建议；item 写形态名（如：疑似W底(颈线12.80)），"
                + "detail 写构成依据（如：两次低点11.52/11.55相差0.3%，第二底缩量）。";
        String user = shortTermHint(ctx) + "股票：" + ctx.getStockName() + "(" + ctx.getStockCode() + ")"
                + " 现价参考：" + (ctx.getRealtime() != null && ctx.getRealtime().getP() != null
                ? ctx.getRealtime().getP() : "见K线最后一根收盘")
                + "\n\n【经典形态知识库】\n" + patternLibText
                + "\n\n【日线K线序列（日期 开/高/低/收 量，最近约80根）】\n" + ctx.getDailyKlineDigest()
                + "\n请对照知识库输出证据卡JSON。";
        return parseCard(AGENT_PATTERN, deepSeekService.chat(system, PromptUtils.limit(user, 14000)));
    }

    // ==================== 基本面+舆情 Agent ====================

    /**
     * 基本面+舆情分析：财务指标 + 实时估值，联网搜索补充近期新闻情绪 → 证据卡。
     */
    public EvidenceCard fundamentalAgent(StockDataContext ctx) {
        String system = "你是基本面与舆情分析师。基于财务指标（ROE/净利增长/营收增长/毛利率/负债率等）、"
                + "实时估值（PE/PB/换手率），并联网搜索该股票近期新闻、公告、行业动态，输出客观证据卡。\n"
                + "规则：1.财务数据以提供的为准，不得编造；2.新闻必须联网搜索获取，"
                + "每条舆情注明大致日期并标注 利好/利空/中性；3.没有搜到相关新闻就如实说明。\n"
                + "输出要求：只输出JSON，格式：" + CARD_FORMAT + "\n"
                + "严禁给出看多/看空/买卖建议；利好利空是新闻性质的客观归类，不是结论。";
        String user = shortTermHint(ctx) + "股票：" + ctx.getStockName() + "(" + ctx.getStockCode() + ")"
                + " 所属市场：" + ctx.getMarket() + " 操作类型：" + ctx.getOperateTypeName() + "\n"
                + "\n【最近财务指标（每期字段：截止日期/ROE%/基本EPS/净利同比%/营收同比%/毛利率%/资产负债率%）】\n"
                + buildFinancialFacts(ctx)
                + "\n【实时估值】" + buildValuationText(ctx)
                + "\n请联网搜索\"" + ctx.getStockName() + "\"近期（近1个月）重要新闻、公告与行业消息，"
                + "结合上述财务数据输出证据卡JSON。";
        return parseCard(AGENT_FUND, deepSeekService.chatWithWebSearch(system, user));
    }

    // ==================== 事实文本构建 ====================

    /**
     * 缠论结构事实（本地 Chan2Engine 计算结果 → 紧凑文本）。
     */
    private String buildChanFacts(StockDataContext ctx) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<StockKlineDTO>> e : ctx.getStockKlines().entrySet()) {
            String level = e.getKey();
            List<StockKlineDTO> klines = e.getValue();
            if (klines == null || klines.size() < 15) {
                continue;
            }
            try {
                List<Chan2Kline> klist = new ArrayList<>(klines.size());
                for (int i = 0; i < klines.size(); i++) {
                    StockKlineDTO d = klines.get(i);
                    double c = d.getC() == null ? 0 : d.getC();
                    Chan2Kline k = new Chan2Kline(d.getT(),
                            d.getO() == null ? c : d.getO(),
                            d.getH() == null ? c : d.getH(),
                            d.getL() == null ? c : d.getL(),
                            c, d.getV() == null ? 0 : d.getV());
                    k.origIndex = i;
                    klist.add(k);
                }
                Chan2Result r = Chan2Engine.analyze(klist, levelPeriod(level), ctx.getStockCode(), 4);
                sb.append('[').append(levelName(level)).append("] 状态=").append(nvl(r.getState()))
                        .append(" 趋势=").append(nvl(r.getTrendDirection()))
                        .append(" 走势类型=").append(nvl(r.getTrendType()));
                if (r.getPivotTopZg() != null) {
                    sb.append(" 中枢ZG=").append(r.getPivotTopZg()).append(" ZD=").append(r.getPivotBottomZd())
                            .append("(").append(nvl(r.getPivotLevel())).append(')');
                } else {
                    sb.append(" 无中枢");
                }
                sb.append(" 顶背驰=").append(Boolean.TRUE.equals(r.getDivergenceTop()) ? "是" : "否")
                        .append(" 底背驰=").append(Boolean.TRUE.equals(r.getDivergenceBottom()) ? "是" : "否");
                if (r.getDivergenceDetail() != null && !r.getDivergenceDetail().isEmpty()) {
                    sb.append(" 背驰详情:").append(String.join("；", r.getDivergenceDetail()));
                }
                sb.append(" 笔数=").append(nvl(r.getBiCount())).append(" 线段数=").append(nvl(r.getSegmentCount()));
                if (r.getDif() != null) {
                    sb.append(" 最新DIF=").append(r.getDif()).append(" DEA=").append(r.getDea())
                            .append(" 柱=").append(r.getHist());
                }
                sb.append(" 最新K线=").append(nvl(r.getLastDate())).append('\n');
            } catch (Exception ex) {
                log.warn("缠论计算失败 {} level={}: {}", ctx.getStockCode(), level, ex.getMessage());
            }
        }
        return sb.length() == 0 ? "缠论结构计算失败（无可用级别）" : sb.toString();
    }

    private String indicatorLine(IndicatorSummary s) {
        return String.format(
                "[%s] 收%.2f(%+.2f%%) MA5=%.2f/MA10=%.2f/MA20=%.2f/MA60=%.2f %s | MACD DIF=%.4f DEA=%.4f %s | "
                        + "KDJ K=%.1f D=%.1f J=%.1f %s | BOLL 上%.2f/中%.2f/下%.2f 位置%.0f%% 带宽%.1f%% %s | 量比%.2f %s | ATR%.2f(%.2f%%)",
                s.getLevelName(), s.getLastClose(), s.getChangePct(),
                s.getMa5(), s.getMa10(), s.getMa20(), s.getMa60(), s.getMaPattern(),
                s.getDif(), s.getDea(), s.getMacdSignal(),
                s.getKdjK(), s.getKdjD(), s.getKdjJ(), s.getKdjSignal(),
                s.getBollUp(), s.getBollMid(), s.getBollLow(), s.getBollPosPct(), s.getBollBandwidthPct(), s.getBollSignal(),
                s.getVolRatio(), s.getVolumeSignal(), s.getAtr(), s.getAtrPct());
    }

    private String buildFinancialFacts(StockDataContext ctx) {
        if (ctx.getFinancials() == null || ctx.getFinancials().isEmpty()) {
            return "财务指标数据不可用";
        }
        StringBuilder sb = new StringBuilder();
        for (StockFinancialIndexDTO f : ctx.getFinancials()) {
            sb.append(String.format("%s: ROE=%s EPS=%s 净利同比=%s%% 营收同比=%s%% 毛利率=%s%% 资产负债率=%s%%%n",
                    nvl(f.getJzrq()), nvl(f.getJzcsyl()), nvl(f.getJbmgsy()),
                    nvl(f.getJlrzz()), nvl(f.getZyyrsrzz()), nvl(f.getMlv()), nvl(f.getZcfzl())));
        }
        return sb.toString();
    }

    private String buildValuationText(StockDataContext ctx) {
        StockRealTimePriceDTO rt = ctx.getRealtime();
        if (rt == null) {
            return "实时行情不可用";
        }
        return String.format("现价=%s PE(TTM)=%s PB=%s 换手率=%s%% 涨跌幅=%s%%",
                nvl(rt.getP()), nvl(rt.getPe()), nvl(rt.getPbRatio()), nvl(rt.getTr()), nvl(rt.getPc()));
    }

    // ==================== 证据卡解析 ====================

    /**
     * 将 LLM 输出解析为证据卡；JSON 解析失败时降级为把原文整体作为一条证据，保证流程不中断。
     */
    private EvidenceCard parseCard(String agentName, String llmText) {
        EvidenceCard card = new EvidenceCard();
        card.setAgent(agentName);
        card.setAgentName(agentName);
        List<EvidenceCard.EvidenceItem> items = new ArrayList<>();
        JSONObject obj = PromptUtils.extractJson(llmText);
        if (obj != null) {
            card.setSummary(PromptUtils.getStr(obj, "summary", ""));
            JSONArray arr = obj.getJSONArray("evidence");
            if (arr != null) {
                for (int i = 0; i < arr.size() && i < 15; i++) {
                    try {
                        JSONObject o = arr.getJSONObject(i);
                        if (o == null) {
                            continue;
                        }
                        EvidenceCard.EvidenceItem it = new EvidenceCard.EvidenceItem();
                        it.setItem(PromptUtils.getStr(o, "item", "证据" + (i + 1)));
                        it.setDetail(PromptUtils.getStr(o, "detail", ""));
                        String strength = PromptUtils.getStr(o, "strength", "中");
                        it.setStrength("强".equals(strength) || "弱".equals(strength) ? strength : "中");
                        // 置信度：模型未给出时按强度换算默认值；缠论证据额外乘折损系数
                        int confidence = o.getIntValue("confidence", defaultConfidence(agentName, it.getStrength()));
                        if (AGENT_CHAN.equals(agentName)) {
                            confidence = (int) (confidence * CHAN_CONFIDENCE_FACTOR);
                        }
                        it.setConfidence(Math.max(0, Math.min(100, confidence)));
                        it.setDirection(normalizeDirection(PromptUtils.getStr(o, "direction", "中性")));
                        it.setSource(PromptUtils.getStr(o, "source", agentName));
                        items.add(it);
                    } catch (Exception ignore) {
                        // 跳过无法解析的单条
                    }
                }
            }
        }
        if (items.isEmpty()) {
            EvidenceCard.EvidenceItem fallback = new EvidenceCard.EvidenceItem();
            fallback.setItem(agentName + "分析原始输出（未按JSON格式返回，降级收录）");
            fallback.setDetail(PromptUtils.limit(llmText == null ? "" : llmText.trim(), 600));
            fallback.setStrength("弱");
            fallback.setConfidence(40);
            fallback.setDirection("中性");
            fallback.setSource(agentName);
            items.add(fallback);
        }
        card.setEvidence(items);
        return card;
    }

    /** 模型未给出 confidence 时，按强度换算默认置信度 */
    private static int defaultConfidence(String agentName, String strength) {
        int base;
        switch (strength) {
            case "强": base = 85; break;
            case "弱": base = 55; break;
            default: base = 70;
        }
        if (AGENT_CHAN.equals(agentName)) {
            base = (int) (base * CHAN_CONFIDENCE_FACTOR);
        }
        return Math.max(0, Math.min(100, base));
    }

    /** 归一化证据信号方向："多"/"空" 归一到多/空，其余归中性 */
    private static String normalizeDirection(String d) {
        if (d == null) {
            return "中性";
        }
        String s = d.trim();
        if (s.contains("中性") || s.contains("无")) {
            return "中性";
        }
        if (s.contains("空")) {
            return "空";
        }
        if (s.contains("多")) {
            return "多";
        }
        return "中性";
    }

    private static String levelName(String level) {
        switch (level) {
            case "5": return "5分钟";
            case "15": return "15分钟";
            case "30": return "30分钟";
            case "60": return "60分钟";
            case "d": return "日线";
            case "w": return "周线";
            case "m": return "月线";
            default: return level;
        }
    }

    /** 与 StockChanServiceImpl 的级别周期口径保持一致：5/15/.../d -> 5min/.../daily */
    private static String levelPeriod(String level) {
        switch (level) {
            case "5": return "5min";
            case "15": return "15min";
            case "30": return "30min";
            case "60": return "60min";
            case "d": return "daily";
            case "w": return "weekly";
            case "m": return "monthly";
            default: return level;
        }
    }

    private static String nvl(Object o) {
        return o == null ? "-" : String.valueOf(o);
    }
}
