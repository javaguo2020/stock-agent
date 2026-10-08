package com.ruoyi.system.agent.service;

import com.ruoyi.system.agent.config.AgentAnalysisProperties;
import com.ruoyi.system.agent.domain.DebateRound;
import com.ruoyi.system.agent.domain.EvidenceCard;
import com.ruoyi.system.agent.domain.JudgeVerdict;
import com.ruoyi.system.agent.domain.PositionAdvice;
import com.ruoyi.system.agent.domain.StockDataContext;
import com.ruoyi.system.agent.util.PromptUtils;
import com.ruoyi.system.service.DeepSeekService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 讨论与决策阶段：首席多头/首席空头有限轮次博弈辩论 + 仓位管理委员 + 法官终裁（证据可信度概率）。
 */
@Slf4j
@Service
public class DebateJudgeService {

    @Autowired
    private DeepSeekService deepSeekService;

    @Autowired
    private AgentAnalysisProperties properties;

    // ==================== 多空辩论 ====================

    /**
     * 执行多空辩论（每轮必须引用至少一条证据卡反驳对方），返回辩论纪要。
     */
    public List<DebateRound> runDebate(List<EvidenceCard> cards, StockDataContext ctx) {
        int rounds = Math.max(1, properties.getDebateRounds());
        List<DebateRound> transcript = new ArrayList<>();
        String evidenceText = formatCards(cards);
        StringBuilder history = new StringBuilder();
        for (int round = 1; round <= rounds; round++) {
            // 多头发言（第1轮仅依据证据卡，其后须反驳空头上一轮观点）
            DebateRound bull = speak(DebateRound.ROLE_BULL, round, evidenceText, history.toString(), ctx);
            transcript.add(bull);
            history.append(formatRound(bull));
            // 空头发言（须反驳多头本轮观点）
            DebateRound bear = speak(DebateRound.ROLE_BEAR, round, evidenceText, history.toString(), ctx);
            transcript.add(bear);
            history.append(formatRound(bear));
        }
        return transcript;
    }

    /**
     * 单个角色的单轮发言。
     */
    private DebateRound speak(String role, int round, String evidenceText, String history, StockDataContext ctx) {
        boolean bull = DebateRound.ROLE_BULL.equals(role);
        String system = bull
                ? "你是\"首席多头\"（BULL），激进派交易员。你的职责：无视风险，只寻找和论证做多逻辑"
                + "（如：放量突破、政策利好、估值修复、主力吸筹、趋势共振）。\n"
                + "规则：1.每轮必须引用至少一条证据卡条目来支持或反驳（标注来源，如\"[技术]KDJ超卖金叉\"）；"
                + "2.若对方引用了不利证据，必须用证据卡中的另一条数据进行反驳（例如对方说\"有顶背离\"，"
                + "你可以用\"量能正在放大可以消化背离\"回应）；3.论点要具体，贴合数据。\n"
                + "输出要求：只输出JSON：{\"argument\":\"本轮论点\",\"citedEvidence\":[\"[来源]证据\"]}"
                + "严禁输出了空仓/卖出/止损等任何空头观点。"
                : "你是\"首席空头\"（BEAR）兼风控官，极度保守。你的职责：只寻找和论证做空/避险逻辑"
                + "（如：套牢盘压力、MACD背离、流动性萎缩、估值泡沫、技术破位、高位滞涨、利空消息）。\n"
                + "规则：1.每轮必须引用至少一条证据卡条目来支持或反驳（标注来源）；"
                + "2.若对方引用了有利证据，必须用证据卡中的另一条数据进行反驳；3.论点要具体，贴合数据。\n"
                + "输出要求：只输出JSON：{\"argument\":\"本轮论点\",\"citedEvidence\":[\"[来源]证据\"]}"
                + "严禁输出看多/买入/加仓等任何多头观点。";
        StringBuilder user = new StringBuilder();
        user.append("辩论标的：").append(ctx.getStockName()).append("(").append(ctx.getStockCode()).append(")")
                .append(" 操作类型：").append(ctx.getOperateTypeName())
                .append(" 现价：").append(ctx.getRealtime() != null && ctx.getRealtime().getP() != null
                ? ctx.getRealtime().getP() : "参考日线最新收盘价").append('\n');
        user.append("\n【全体子Agent证据卡（客观事实）】\n").append(evidenceText);
        if (history != null && !history.isEmpty()) {
            user.append("\n【辩论历史】\n").append(history);
        }
        user.append("\n现在是第").append(round).append("轮，你（").append(bull ? "多头" : "空头")
                .append("）请发言。只输出JSON。");

        DebateRound r = new DebateRound();
        r.setRound(round);
        r.setRole(role);
        r.setRoleName(bull ? "首席多头" : "首席空头");
        String text = deepSeekService.chat(system, user.toString());
        com.alibaba.fastjson2.JSONObject obj = PromptUtils.extractJson(text);
        if (obj != null) {
            r.setArgument(PromptUtils.limit(PromptUtils.getStr(obj, "argument", text == null ? "" : text.trim()), 800));
            r.setCitedEvidence(PromptUtils.getStrList(obj, "citedEvidence"));
        } else {
            r.setArgument(PromptUtils.limit(text == null ? "" : text.trim(), 800));
            r.setCitedEvidence(new ArrayList<>());
        }
        return r;
    }

    // ==================== 仓位管理委员 ====================

    /**
     * 仓位管理委员：基于证据可信度评估的向上概率 + 波动率 + 资产类型 + 风险偏好，动态给出仓位建议。
     *
     * <p>纯确定性计算，不再依赖大模型，修复此前模型不稳定导致始终输出固定 20% 仓位的问题。</p>
     */
    public PositionAdvice positionSizer(List<EvidenceCard> cards, List<DebateRound> transcript, StockDataContext ctx) {
        EvidenceAssessment assessment = assessEvidence(cards, ctx.getOperateType());
        int riskPreference = clamp(ctx.getRiskPreference(), 1, 3);
        int pct = computePosition(assessment.upProbability, ctx.getAtrPct(), ctx.getLimitPct(), riskPreference);

        PositionAdvice advice = new PositionAdvice();
        advice.setPositionPct(pct);
        advice.setUpProbability(assessment.upProbability);
        advice.setVolatilityNote(buildVolatilityNote(ctx));
        advice.setReason(buildPositionReason(assessment, ctx, riskPreference, pct));
        return advice;
    }

    // ==================== 法官终裁 ====================

    /**
     * 法官（Chief Judge）终裁：核心基于证据可信度的向上/向下概率，而非证据数量；
     * 大模型仅负责价格规划（买入区/止损/目标/风险回报）与理由叙述。
     */
    public JudgeVerdict judge(List<EvidenceCard> cards, List<DebateRound> transcript,
                              PositionAdvice position, StockDataContext ctx) {
        // 1. 确定性证据可信度评估：得到操作周期内的向上概率与多空胜方
        EvidenceAssessment assessment = assessEvidence(cards, ctx.getOperateType());
        String winner = assessment.winner;
        int upProb = assessment.upProbability;

        // 2. 大模型：基于权威概率与胜方，只负责价格规划与理由（不再决定胜负/概率，避免被证据数量误导）
        String text = deepSeekService.chat(judgeSystem(),
                judgeUser(cards, transcript, position, ctx, assessment, winner, upProb));
        com.alibaba.fastjson2.JSONObject obj = PromptUtils.extractJson(text);

        // 3. 解析价格规划（容错）
        String entryPrice = "";
        double stopLoss = 0, targetPrice = 0, llmRr = 0;
        List<String> keyFactors = new ArrayList<>();
        String modelReason = "";
        if (obj != null) {
            entryPrice = PromptUtils.getStr(obj, "entryPrice", "");
            stopLoss = PromptUtils.getDouble(obj, "stopLoss", 0);
            targetPrice = PromptUtils.getDouble(obj, "targetPrice", 0);
            llmRr = PromptUtils.getDouble(obj, "riskReward", 0);
            keyFactors = PromptUtils.getStrList(obj, "keyFactors");
            modelReason = PromptUtils.getStr(obj, "reasoning", "");
        }
        // 风险回报比：优先由价格计划推导，模型值兜底
        double riskReward = resolveRiskReward(entryPrice, stopLoss, targetPrice, llmRr);

        // 4. 确定性终裁：由向上概率 + 风险回报 + 仓位三条件决定
        String decision = decide(upProb, riskReward, position.getPositionPct());

        JudgeVerdict v = new JudgeVerdict();
        v.setWinner(winner);
        v.setDecision(decision);
        v.setDecisionLabel(decisionLabel(decision));
        v.setUpProbability(upProb);
        v.setRiskReward(riskReward);
        v.setPositionPct(position.getPositionPct());
        v.setEntryPrice(entryPrice);
        v.setStopLoss(stopLoss);
        v.setTargetPrice(targetPrice);
        v.setReasoning(buildVerdictReasoning(assessment, decision, riskReward, position, modelReason));
        v.setKeyFactors(keyFactors);
        return v;
    }

    // ==================== 工具 ====================

    /**
     * 证据卡列表 → 紧凑文本（辩论与法官共用输入）。
     */
    public String formatCards(List<EvidenceCard> cards) {
        StringBuilder sb = new StringBuilder();
        if (cards != null) {
            for (EvidenceCard c : cards) {
                sb.append("■ ").append(c.getAgentName()).append("Agent证据卡：")
                        .append(c.getSummary() == null ? "" : c.getSummary()).append('\n');
                if (c.getEvidence() != null) {
                    for (EvidenceCard.EvidenceItem it : c.getEvidence()) {
                        sb.append("  - [").append(it.getSource()).append("] ").append(it.getItem())
                                .append("（强度:").append(it.getStrength()).append(" 置信度:")
                                .append(it.getConfidence() == null ? "-" : it.getConfidence())
                                .append(" 方向:").append(it.getDirection() == null ? "中性" : it.getDirection())
                                .append("）")
                                .append(it.getDetail() == null || it.getDetail().isEmpty() ? "" : "：" + it.getDetail())
                                .append('\n');
                    }
                }
            }
        }
        return sb.toString();
    }

    private String formatRound(DebateRound r) {
        StringBuilder sb = new StringBuilder();
        sb.append("【第").append(r.getRound()).append("轮-").append(r.getRoleName()).append("】")
                .append(r.getArgument()).append('\n');
        if (r.getCitedEvidence() != null && !r.getCitedEvidence().isEmpty()) {
            sb.append("  引用: ").append(String.join("；", r.getCitedEvidence())).append('\n');
        }
        return sb.toString();
    }

    private String decisionLabel(String decision) {
        switch (decision) {
            case "BUY": return "买入";
            case "SELL": return "卖出";
            case "HOLD": return "继续持有";
            default: return "观望";
        }
    }

    // ==================== 证据可信度评估（确定性评分模型） ====================

    /** 时间周期权重矩阵：行=级别(5分/15分/30分/60分/日/周/月)，列=操作类型(短期/中长期/长期) */
    private static final double[][] LEVEL_WEIGHTS = {
            {1.30, 0.40, 0.20}, // 5分钟
            {1.40, 0.60, 0.30}, // 15分钟
            {1.20, 0.80, 0.40}, // 30分钟
            {1.00, 1.00, 0.60}, // 60分钟
            {0.70, 1.30, 1.00}, // 日线
            {0.40, 0.90, 1.20}, // 周线
            {0.30, 0.50, 1.40}  // 月线
    };
    /** 消息面（舆情/新闻/公告等）证据权重：时效性强，短期影响力大、长期消退快 */
    private static final double[] NEWS_WEIGHTS = {1.25, 0.90, 0.60};
    /** 基本面（财报/估值）证据权重：长期价值证据，短期影响小 */
    private static final double[] FUND_WEIGHTS = {0.80, 1.10, 1.30};

    /** 证据可信度评估中间结果。 */
    public static class EvidenceAssessment {
        double bullScore;
        double bearScore;
        int bullCount;
        int bearCount;
        int upProbability;
        String winner;
    }

    /**
     * 证据可信度评估：按「置信度 × 时间周期匹配度 × 消息时效性」加权汇总多空证据，
     * 得到用户选定操作周期内股价向上运动的概率，而非简单比较证据数量。
     *
     * <p>示例：短期操作下，空头证据「日线下跌趋势」（日线权重0.7）可信度被压低，
     * 而「15分钟底背离」（1.4）+「5分钟二买」（1.3）+「重大利好」（消息面1.25）加权占优，
     * 最终向上概率偏高。</p>
     */
    private EvidenceAssessment assessEvidence(List<EvidenceCard> cards, int operateType) {
        EvidenceAssessment ass = new EvidenceAssessment();
        if (cards != null) {
            for (EvidenceCard card : cards) {
                if (card.getEvidence() == null) {
                    continue;
                }
                for (EvidenceCard.EvidenceItem it : card.getEvidence()) {
                    int dir = directionOf(it);
                    if (dir == 0) {
                        continue;
                    }
                    double score = scoreItem(it, operateType);
                    if (dir > 0) {
                        ass.bullScore += score;
                        ass.bullCount++;
                    } else {
                        ass.bearScore += score;
                        ass.bearCount++;
                    }
                }
            }
        }
        double total = ass.bullScore + ass.bearScore;
        ass.upProbability = total <= 0 ? 50 : (int) Math.round(100.0 * ass.bullScore / total);
        if (ass.upProbability >= 58) {
            ass.winner = "BULL";
        } else if (ass.upProbability <= 42) {
            ass.winner = "BEAR";
        } else {
            ass.winner = "TIE";
        }
        return ass;
    }

    /** 出处维度权重：时间周期关联性 + 消息时效性/影响力（按操作周期匹配） */
    private static double sourceWeight(String source, int operateType) {
        int idx = clamp(operateType, 1, 3) - 1;
        if (source == null || source.isEmpty()) {
            return 1.0;
        }
        // 消息面：突发新闻/公告/美股概念等，短期影响力大、长期消退快
        if (containsAny(source, "舆情", "新闻", "公告", "资讯", "消息", "突发", "美股", "概念",
                "政策", "传闻", "评级", "利好", "利空")) {
            return NEWS_WEIGHTS[idx];
        }
        // 基本面/财务：长期价值证据
        if (containsAny(source, "财务", "财报", "基本面", "估值", "ROE", "PE", "PB", "换手",
                "业绩", "分红", "利润", "营收", "负债")) {
            return FUND_WEIGHTS[idx];
        }
        // 技术信号的时间周期匹配
        if (source.contains("5分钟")) return LEVEL_WEIGHTS[0][idx];
        if (source.contains("15分钟")) return LEVEL_WEIGHTS[1][idx];
        if (source.contains("30分钟")) return LEVEL_WEIGHTS[2][idx];
        if (source.contains("60分钟")) return LEVEL_WEIGHTS[3][idx];
        if (source.contains("日线") || source.contains("日K")) return LEVEL_WEIGHTS[4][idx];
        if (source.contains("周线")) return LEVEL_WEIGHTS[5][idx];
        if (source.contains("月线")) return LEVEL_WEIGHTS[6][idx];
        return 1.0;
    }

    /** 证据方向：+1=多 / -1=空 / 0=中性。优先 direction 字段，缺失或"中性"时按关键词兜底识别强信号。 */
    private static int directionOf(EvidenceCard.EvidenceItem it) {
        String d = it.getDirection();
        if (d != null) {
            if (d.contains("空")) return -1;
            if (d.contains("多")) return 1;
            // "中性"或其它：继续走关键词兜底
        }
        String text = (it.getItem() == null ? "" : it.getItem())
                + (it.getDetail() == null ? "" : it.getDetail());
        boolean bull = containsAny(text, "金叉", "底背", "底背离", "超卖", "一买", "二买", "三买", "突破",
                "放量上", "多头排列", "买点", "看多", "翻多", "上攻", "启动", "放量突破");
        boolean bear = containsAny(text, "死叉", "顶背", "顶背离", "超买", "一卖", "二卖", "三卖", "破位",
                "放量下", "空头排列", "卖点", "看空", "翻空", "下跌", "滞涨", "跌破");
        if (bull && !bear) return 1;
        if (bear && !bull) return -1;
        return 0;
    }

    /** 单条证据可信度得分 = 置信度 × 出处维度权重 */
    private static double scoreItem(EvidenceCard.EvidenceItem it, int operateType) {
        int conf = it.getConfidence() == null ? 50 : it.getConfidence();
        conf = Math.max(0, Math.min(100, conf));
        return conf * sourceWeight(it.getSource(), operateType);
    }

    /**
     * 动态仓位计算（0-80，10 的整数倍）。
     * <ol>
     *   <li>由向上概率映射基础仓位；</li>
     *   <li>风险过高且预期收益偏低时直接归零（空仓）；</li>
     *   <li>按波动率（ATR%）设上限；</li>
     *   <li>按资产类型（涨跌停幅度）折减；</li>
     *   <li>按用户风险偏好限定预设最高仓位。</li>
     * </ol>
     */
    private int computePosition(int upProbability, double atrPct, double limitPct, int riskPreference) {
        int base;
        if (upProbability >= 80) base = 80;
        else if (upProbability >= 75) base = 70;
        else if (upProbability >= 65) base = 60;
        else if (upProbability >= 55) base = 40;
        else if (upProbability >= 45) base = 20;
        else if (upProbability >= 35) base = 10;
        else base = 0;

        // 风险过高（高波动 / 高振幅资产）且预期收益偏低（向上概率<50） => 空仓
        boolean highRisk = atrPct > 5.0 || limitPct >= 20;
        if (highRisk && upProbability < 50) {
            return 0;
        }

        // 波动率上限：高波动更保守
        if (atrPct > 5.0) base = Math.min(base, 20);
        else if (atrPct >= 3.0) base = Math.min(base, 50);

        // 资产类型风险折减（创业板/科创板 20%、北交所 30% 波动更大）
        if (limitPct >= 30) base = (int) (base * 0.70);
        else if (limitPct >= 20) base = (int) (base * 0.85);

        // 风险偏好：限定预设最高仓位
        int maxByPref = riskPreference == 1 ? 30 : riskPreference == 3 ? 80 : 60;
        base = Math.min(maxByPref, base);

        base = Math.round(base / 10f) * 10;
        return clamp(base, 0, 80);
    }

    /** 确定性终裁规则：由向上概率 + 风险回报 + 仓位三条件决定，而非比较证据数量。 */
    private String decide(int upProb, double riskReward, int positionPct) {
        if (upProb >= 65 && riskReward > 2.5 && positionPct >= 50) {
            return "BUY";
        }
        if (upProb <= 35) {
            return "SELL";
        }
        if (upProb >= 55) {
            return "HOLD";
        }
        return "WATCH";
    }

    private String judgeSystem() {
        return "你是\"首席法官\"（Chief Judge）的价格规划助手。多空胜负与向上概率已由系统按证据可信度确定性计算得出，"
                + "你只需在给定终裁（decision 已确定）基础上，给出合理的价格规划与裁决理由。\n"
                + "规则：1.entryPrice 为建议买入价格区间（如 12.30-12.50）；2.stopLoss 为止损价（低于现价）；"
                + "3.targetPrice 为目标价（高于现价）；4.riskReward 为风险回报比（=(目标价-入场价)/(入场价-止损价)，>0）；"
                + "5.reasoning 需结合多空证据、操作周期与给定向上概率说明理由；6.keyFactors 列出关键考量因素。\n"
                + "输出要求：只输出JSON：{\"entryPrice\":\"12.30-12.50\",\"stopLoss\":11.5,\"targetPrice\":15.8,"
                + "\"riskReward\":2.8,\"reasoning\":\"裁决理由\",\"keyFactors\":[\"关键因素1\"]}";
    }

    private String judgeUser(List<EvidenceCard> cards, List<DebateRound> transcript, PositionAdvice position,
                             StockDataContext ctx, EvidenceAssessment ass, String winner, int upProb) {
        StringBuilder user = new StringBuilder();
        user.append("标的：").append(ctx.getStockName()).append("(").append(ctx.getStockCode()).append(")")
                .append(" 操作类型：").append(ctx.getOperateTypeName())
                .append(" 现价：").append(ctx.getRealtime() != null && ctx.getRealtime().getP() != null
                ? ctx.getRealtime().getP() : "参考日线最新收盘价").append('\n');
        user.append("\n【证据可信度评估（系统已确定性计算）】多头证据").append(ass.bullCount)
                .append("条(加权分").append(String.format("%.0f", ass.bullScore)).append(")，空头证据")
                .append(ass.bearCount).append("条(加权分").append(String.format("%.0f", ass.bearScore)).append(")，")
                .append("胜方=").append(winnerLabelZh(winner)).append("，向上概率=").append(upProb).append("%\n");
        user.append("\n【证据卡】\n").append(formatCards(cards));
        user.append("\n【辩论纪要】\n");
        if (transcript != null) {
            for (DebateRound r : transcript) {
                user.append(formatRound(r));
            }
        }
        user.append("\n【仓位管理委员建议】仓位").append(position.getPositionPct()).append("%\n");
        user.append("\n请给出价格规划与裁决理由JSON。");
        return user.toString();
    }

    private String buildVerdictReasoning(EvidenceAssessment ass, String decision, double riskReward,
                                         PositionAdvice position, String modelReason) {
        StringBuilder sb = new StringBuilder();
        sb.append("证据可信度评估：多头证据").append(ass.bullCount).append("条/空头证据").append(ass.bearCount)
                .append("条，向上概率").append(ass.upProbability).append("%，风险回报比1:").append(riskReward)
                .append("，仓位").append(position.getPositionPct()).append("%；终裁=").append(decisionLabel(decision))
                .append("。");
        if (modelReason != null && !modelReason.isEmpty()) {
            sb.append(' ').append(modelReason);
        }
        return PromptUtils.limit(sb.toString(), 1000);
    }

    private String buildVolatilityNote(StockDataContext ctx) {
        double pct = ctx.getAtrPct();
        String level = pct > 5.0 ? "高波动" : pct >= 3.0 ? "中等波动" : "低波动";
        return String.format("日线ATR=%.2f（%.2f%%），波动率：%s", ctx.getAtr(), pct, level);
    }

    private String buildPositionReason(EvidenceAssessment ass, StockDataContext ctx, int riskPreference, int pct) {
        String prefName = riskPreference == 1 ? "保守" : riskPreference == 3 ? "激进" : "稳健";
        StringBuilder sb = new StringBuilder();
        sb.append("基于证据可信度评估：多头证据").append(ass.bullCount).append("条（加权分")
                .append(String.format("%.0f", ass.bullScore)).append("）、空头证据").append(ass.bearCount)
                .append("条（加权分").append(String.format("%.0f", ass.bearScore)).append("），")
                .append(ctx.getOperateTypeName()).append("周期内向上概率").append(ass.upProbability).append("%；");
        if (pct == 0) {
            sb.append("风险过高且预期收益偏低，建议空仓规避。");
        } else {
            sb.append("综合波动率").append(String.format("%.2f%%", ctx.getAtrPct()))
                    .append("、涨跌停幅度").append(String.format("%.0f%%", ctx.getLimitPct()))
                    .append("、风险偏好（").append(prefName).append("），给出仓位").append(pct).append("%。");
        }
        return sb.toString();
    }

    /** 风险回报比：优先由价格计划推导，模型值兜底。 */
    private double resolveRiskReward(String entryPrice, double stopLoss, double targetPrice, double llmRr) {
        double entry = parseEntryMid(entryPrice);
        if (entry > 0 && stopLoss > 0 && targetPrice > 0 && targetPrice > entry && entry > stopLoss) {
            double rr = (targetPrice - entry) / (entry - stopLoss);
            if (rr > 0) {
                return Math.round(rr * 10) / 10.0;
            }
        }
        return llmRr;
    }

    /** 解析价格区间中间价；单个数字取自身。 */
    private double parseEntryMid(String entryPrice) {
        if (entryPrice == null || entryPrice.isEmpty()) {
            return 0;
        }
        String s = entryPrice.replace("，", " ").replace(",", " ").replace("~", "-").trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+(?:\\.\\d+)?").matcher(s);
        double first = 0, last = 0;
        int count = 0;
        while (m.find()) {
            double val = Double.parseDouble(m.group());
            if (count == 0) first = val;
            last = val;
            count++;
        }
        if (count == 0) return 0;
        if (count == 1) return first;
        return (first + last) / 2.0;
    }

    private String winnerLabelZh(String winner) {
        if ("BULL".equals(winner)) return "多头";
        if ("BEAR".equals(winner)) return "空头";
        return "平局";
    }

    private static boolean containsAny(String text, String... keys) {
        for (String k : keys) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
