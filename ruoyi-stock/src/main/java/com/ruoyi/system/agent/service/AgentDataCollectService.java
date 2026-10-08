package com.ruoyi.system.agent.service;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.agent.domain.AgentAnalysisRequest;
import com.ruoyi.system.agent.domain.IndicatorSummary;
import com.ruoyi.system.agent.domain.StockDataContext;
import com.ruoyi.system.agent.enums.OperateType;
import com.ruoyi.system.agent.util.IndicatorCalculator;
import com.ruoyi.system.domain.StockFinancialIndexDTO;
import com.ruoyi.system.domain.StockIndexMinDTO;
import com.ruoyi.system.domain.StockKlineDTO;
import com.ruoyi.system.domain.StockRealTimePriceDTO;
import com.ruoyi.system.util.StockDataUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据采集编排服务（主 Agent 的数据获取决策）。
 *
 * <p>由 Java 规则引擎按"操作类型 + 股票所属市场"决定拉取哪些级别的 K 线与指数数据，
 * 不消耗大模型 Token；指标计算全部在本地完成，只把摘要交给后续 LLM 节点。</p>
 */
@Slf4j
@Service
public class AgentDataCollectService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 级别中文名 */
    private static final Map<String, String> LEVEL_NAMES = Map.of(
            "5", "5分钟", "15", "15分钟", "30", "30分钟", "60", "60分钟",
            "d", "日线", "w", "周线", "m", "月线");

    /** 个股 K 线级别与条数：操作类型 -> (级别 -> 条数)。条数按缠论分析需要取足 */
    private static final Map<Integer, LinkedHashMap<String, Integer>> STOCK_LEVELS = Map.of(
            1, levels(entry("5", 1500), entry("15", 1200), entry("30", 1000), entry("60", 800), entry("d", 300)),
            2, levels(entry("30", 1000), entry("60", 800), entry("d", 500), entry("w", 250)),
            3, levels(entry("d", 800), entry("w", 400), entry("m", 240)));

    /** 指数 K 线级别与回看天数：操作类型 -> (级别 -> 回看自然日天数) */
    private static final Map<Integer, LinkedHashMap<String, Integer>> INDEX_LEVELS = Map.of(
            1, levels(entry("5", 14), entry("15", 14), entry("d", 120)),
            2, levels(entry("60", 45), entry("d", 365), entry("w", 800)),
            3, levels(entry("d", 365 * 5), entry("w", 365 * 10)));

    /** 监控模式（短线实时交易）个股K线级别：仅15分钟+日线，数量缩减节约Token */
    private static final LinkedHashMap<String, Integer> MONITOR_STOCK_LEVELS = levels(
            entry("15", 60), entry("d", 150));

    /** 监控模式指数级别：仅15分钟+日线 */
    private static final LinkedHashMap<String, Integer> MONITOR_INDEX_LEVELS = levels(
            entry("15", 14), entry("d", 120));

    @Autowired
    private StockDataUtils dataUtils;

    /**
     * 采集指定股票的全部分析数据。
     *
     * @throws RuntimeException 必需数据（个股K线）全部获取失败时抛出
     */
    public StockDataContext collect(AgentAnalysisRequest request) {
        OperateType type = OperateType.of(request.getOperateType());
        boolean monitorMode = request.isMonitorMode();
        String code = normalizeCode(request.getStockCode());
        StockDataContext ctx = new StockDataContext();
        ctx.setStockCode(code);
        ctx.setOperateType(type.getCode());
        ctx.setOperateTypeName(type.getLabel());
        int riskPref = request.getRiskPreference() == null ? 2 : request.getRiskPreference();
        ctx.setRiskPreference(riskPref < 1 || riskPref > 3 ? 2 : riskPref);
        ctx.setMarket(resolveMarket(code));
        ctx.setLimitPct(resolveLimitPct(code));
        ctx.setStockName(resolveName(code));
        ctx.setCollectTime(LocalDateTime.now().format(FULL));
        // 短线监控模式：子Agent提示标注"短线交易"，只关注短期走势
        ctx.setShortTermMonitor(monitorMode && type.getCode() == 1);

        // 1. 个股 K 线（前复权，避免除权跳空干扰形态与均线）
        Map<String, Integer> stockLevels = (monitorMode && type.getCode() == 1)
                ? MONITOR_STOCK_LEVELS : STOCK_LEVELS.get(type.getCode());
        for (Map.Entry<String, Integer> e : stockLevels.entrySet()) {
            String level = e.getKey();
            try {
                List<StockKlineDTO> klines = dataUtils.getHistoryData(code, level, "n", "", e.getValue());
                if (klines != null && !klines.isEmpty()) {
                    ctx.getStockKlines().put(level, klines);
                    ctx.getIndicators().put(level, IndicatorCalculator.summarize(klines, level));
                }
            } catch (Exception ex) {
                log.warn("采集K线失败 code={} level={}: {}", code, level, ex.getMessage());
            }
        }
        if (ctx.getStockKlines().isEmpty()) {
            throw new RuntimeException("股票K线数据获取失败: " + code);
        }

        // 2. 实时行情（含 PE/PB/换手率）
        try {
            ctx.setRealtime(dataUtils.getRealTimePrice(code.split("\\.")[0]));
        } catch (Exception ex) {
            log.warn("采集实时行情失败 code={}: {}", code, ex.getMessage());
        }

        // 3. 日线衍生：ATR / 涨跌停统计 / K线压缩文本（形态分析用）
        List<StockKlineDTO> daily = ctx.getStockKlines().get("d");
        if (daily != null && !daily.isEmpty()) {
            IndicatorSummary dailySummary = ctx.getIndicators().get("d");
            ctx.setAtr(dailySummary.getAtr());
            ctx.setAtrPct(dailySummary.getAtrPct());
            IndicatorCalculator.fillLimitUpStats(ctx.getLimitUpStats(), daily, ctx.getLimitPct(), ctx.getRealtime());
            ctx.setDailyKlineDigest(IndicatorCalculator.dailyDigest(daily, 80));
        }

        // 4. 财务主要指标（开始=3年前，结束=前一天，确保有数据）
        try {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            LocalDate threeYearsAgo = LocalDate.now().minusYears(3);
            List<StockFinancialIndexDTO> fin = dataUtils.getFinancialIndex(code,
                    threeYearsAgo.format(YYYYMMDD), yesterday.format(YYYYMMDD));
            if (fin != null && fin.size() > 3) {
                ctx.setFinancials(fin.subList(fin.size() - 3, fin.size()));
            } else {
                ctx.setFinancials(fin);
            }
        } catch (Exception ex) {
            log.warn("采集财务指标失败 code={}: {}", code, ex.getMessage());
        }

        // 5. 指数数据（所属市场指数 + 沪深300）
        collectIndices(ctx, type.getCode(), monitorMode);
        return ctx;
    }

    /**
     * 采集指数分时数据并生成摘要文本。
     */
    private void collectIndices(StockDataContext ctx, int operateType, boolean monitorMode) {
        Map<String, String> indexCodes = resolveIndexCodes(ctx.getStockCode());
        Map<String, Integer> indexLevels = (monitorMode && operateType == 1)
                ? MONITOR_INDEX_LEVELS : INDEX_LEVELS.get(operateType);
        LocalDate today = LocalDate.now();
        for (Map.Entry<String, String> idx : indexCodes.entrySet()) {
            String idxName = idx.getKey();
            String idxCode = idx.getValue();
            for (Map.Entry<String, Integer> lv : indexLevels.entrySet()) {
                String period = lv.getKey();
                String st = today.minusDays(lv.getValue()).format(YYYYMMDD);
                try {
                    List<StockIndexMinDTO> list = dataUtils.getIndexHistoryMinData(idxCode, period, st, "");
                    if (list == null || list.isEmpty()) {
                        continue;
                    }
                    String key = idxName + "@" + period;
                    ctx.getIndexKlines().put(key, list);
                    ctx.getIndexSummaries().put(key + "（" + LEVEL_NAMES.getOrDefault(period, period) + "）",
                            summarizeIndex(list, idxName, LEVEL_NAMES.getOrDefault(period, period)));
                } catch (Exception ex) {
                    log.warn("采集指数数据失败 {} {} {}: {}", idxName, idxCode, period, ex.getMessage());
                }
            }
        }
    }

    /**
     * 生成指数一句话摘要：最新点位、涨跌幅、近期高低点、短期趋势。
     */
    private String summarizeIndex(List<StockIndexMinDTO> list, String name, String levelName) {
        int n = list.size();
        StockIndexMinDTO last = list.get(n - 1);
        double close = parse(last.getCfloat());
        double prev = parse(last.getPcfloat());
        if (prev <= 0 && n > 1) {
            prev = parse(list.get(n - 2).getCfloat());
        }
        double pct = prev > 0 ? (close - prev) / prev * 100 : 0;
        int from = Math.max(0, n - 20);
        double hi = -Double.MAX_VALUE, lo = Double.MAX_VALUE;
        for (int i = from; i < n; i++) {
            hi = Math.max(hi, parse(list.get(i).getHfloat()));
            lo = Math.min(lo, parse(list.get(i).getLfloat()));
        }
        // 近5根趋势
        int trendFrom = Math.max(0, n - 5);
        double slope = close - parse(list.get(trendFrom).getCfloat());
        String trend = slope > 0 ? "近5根向上" : slope < 0 ? "近5根回落" : "近5根横盘";
        return String.format("%s(%s): 最新%.2f 涨跌%.2f%% 近20根高%.2f/低%.2f %s",
                name, levelName, close, pct, hi, lo, trend);
    }

    /**
     * 根据股票代码确定应关注的指数：所属市场核心指数 + 沪深300。
     */
    private Map<String, String> resolveIndexCodes(String code) {
        Map<String, String> map = new LinkedHashMap<>();
        String pure = code.split("\\.")[0];
        if (pure.startsWith("688") || pure.startsWith("689")) {
            map.put("上证指数", "000001.SH");
        } else if (pure.startsWith("300") || pure.startsWith("301") || pure.startsWith("302")) {
            map.put("创业板指", "399006.SZ");
        } else if (pure.startsWith("6") || pure.startsWith("9")) {
            map.put("上证指数", "000001.SH");
        } else if (pure.startsWith("4") || pure.startsWith("8")) {
            map.put("深证成指", "399001.SZ");
        } else {
            map.put("深证成指", "399001.SZ");
        }
        map.put("沪深300", "000300.SH");
        return map;
    }

    /**
     * 解析市场说明。
     */
    private String resolveMarket(String code) {
        String pure = code.split("\\.")[0];
        if (pure.startsWith("688") || pure.startsWith("689")) {
            return "科创板";
        }
        if (pure.startsWith("300") || pure.startsWith("301") || pure.startsWith("302")) {
            return "创业板";
        }
        if (pure.startsWith("4") || pure.startsWith("8") || pure.startsWith("92")) {
            return "北交所";
        }
        return code.endsWith(".SH") ? "沪市主板" : "深市主板";
    }

    /**
     * 解析涨跌停幅度（%）：主板10、创业板/科创板20、北交所30。ST 股以实时数据为准，此处不区分。
     */
    private double resolveLimitPct(String code) {
        String pure = code.split("\\.")[0];
        if (pure.startsWith("688") || pure.startsWith("689")
                || pure.startsWith("300") || pure.startsWith("301") || pure.startsWith("302")) {
            return 20;
        }
        if (pure.startsWith("4") || pure.startsWith("8") || pure.startsWith("92")) {
            return 30;
        }
        return 10;
    }

    /**
     * 从股票基本信息中解析名称（带 Redis 缓存，开销低）。
     */
    private String resolveName(String code) {
        try {
            String pure = code.split("\\.")[0];
            List<Map<String, String>> all = dataUtils.getAllStocks();
            if (all != null) {
                for (Map<String, String> m : all) {
                    if (pure.equals(m.get("code"))) {
                        return m.get("name");
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("解析股票名称失败 code={}: {}", code, ex.getMessage());
        }
        return code;
    }

    /**
     * 代码归一化：000001 -> 000001.SZ（与缠论服务保持一致）。
     */
    private String normalizeCode(String code) {
        if (StringUtils.isBlank(code)) {
            throw new RuntimeException("股票代码不能为空");
        }
        String c = code.trim().toUpperCase();
        if (c.contains(".")) {
            return c;
        }
        if (c.length() == 6 && c.chars().allMatch(Character::isDigit)) {
            char first = c.charAt(0);
            String suffix;
            switch (first) {
                case '6':
                case '9':
                    suffix = "SH";
                    break;
                case '4':
                case '8':
                    suffix = "BJ";
                    break;
                default:
                    suffix = "SZ";
            }
            return c + "." + suffix;
        }
        return c;
    }

    private static double parse(String s) {
        try {
            return s == null || s.isEmpty() ? 0 : Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @SafeVarargs
    private static LinkedHashMap<String, Integer> levels(Map.Entry<String, Integer>... entries) {
        LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : entries) {
            map.put(e.getKey(), e.getValue());
        }
        return map;
    }

    private static Map.Entry<String, Integer> entry(String k, int v) {
        return Map.entry(k, v);
    }

    /**
     * 输出采集概要日志。
     */
    public void logCollectSummary(StockDataContext ctx) {
        List<String> lv = new ArrayList<>(ctx.getStockKlines().keySet());
        log.info("数据采集完成: {} {} 市场={} 级别={} 指数块={} 财务期数={} ATR%={}",
                ctx.getStockCode(), ctx.getStockName(), ctx.getMarket(), lv,
                ctx.getIndexKlines().size(), ctx.getFinancials() == null ? 0 : ctx.getFinancials().size(),
                ctx.getAtrPct());
    }
}
