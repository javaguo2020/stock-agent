package com.ruoyi.system.agent.task;

import com.ruoyi.system.agent.domain.QuickAnalysisResult;
import com.ruoyi.system.agent.service.AgentQuickAnalysisService;
import com.ruoyi.system.domain.AiWatchStock;
import com.ruoyi.system.domain.StockAiTradeStat;
import com.ruoyi.system.domain.StockRealTimePriceDTO;
import com.ruoyi.system.service.IAiWatchStockService;
import com.ruoyi.system.service.IStockAiTradeStatService;
import com.ruoyi.system.service.StockTradeService;
import com.ruoyi.system.util.StockCodeUtils;
import com.ruoyi.system.util.StockDataUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * 多 Agent 实时监控定时任务。
 *
 * <p>从数据库 ai_watch_stock（启用项）读取监控股票池，
 * 盘中多次（10:00 / 13:30 / 14:30）触发，异步调用方法调用模式的多 Agent 分析；
 * 当分析出 BUY/SELL 决策时，调用远程买入/卖出接口。</p>
 */
@Slf4j
@Component("agentMonitorTask")
public class AgentMonitorTask {

    /** 监控使用的操作类型：1=短期 */
    private static final int OPERATE_TYPE = 1;

    /** 定时任务触发的交易默认策略号 */
    private static final String TRADE_STRATEGY_NO = "AI";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private AgentQuickAnalysisService quickService;

    @Autowired
    private IAiWatchStockService aiWatchStockService;

    @Autowired
    private StockTradeService stockTradeService;

    @Autowired
    private StockDataUtils stockDataUtils;

    @Autowired
    private IStockAiTradeStatService stockAiTradeStatService;

    /** 项目统一异步线程池（AsyncConfig#taskExecutor） */
    @Autowired
    @Qualifier("taskExecutor")
    private Executor taskExecutor;

    /**
     * 盘中触发一：10:00 与 14:30（工作日）。（已关闭 AI 定时监控）
     */
//    @Scheduled(cron = "0 0 10,14 ? * MON-FRI", zone = "Asia/Shanghai")
    public void monitorOpenAndClose() {
        runMonitor();
    }

    /**
     * 盘中触发二：13:30（工作日）。
     */
//    @Scheduled(cron = "0 30 13 ? * MON-FRI", zone = "Asia/Shanghai")
    public void monitorNoon() {
        runMonitor();
    }

    /**
     * 执行一轮监控：非交易日跳过；从数据库读取启用股票池，逐只异步分析并触发交易。
     */
    public void runMonitor() {
        String today = LocalDate.now().format(DATE_FMT);
        if (!stockDataUtils.isTradingDay(today)) {
            log.info("[多Agent监控] 今日非交易日({})，跳过", today);
            return;
        }
        List<AiWatchStock> watchList = aiWatchStockService.selectEnabledAiWatchStocks();
        if (watchList == null || watchList.isEmpty()) {
            log.info("[多Agent监控] AI实时关注股票池为空，本轮结束");
            return;
        }
        log.info("[多Agent监控] 开始一轮监控，共{}只标的: {}", watchList.size(),
                watchList.stream().map(AiWatchStock::getStockCode).toList());
        for (AiWatchStock watch : watchList) {
            String code = watch.getStockCode();
            if (quickService.isRunning(code, OPERATE_TYPE)) {
                log.info("[多Agent监控] {} 正在分析中，本轮跳过", code);
                continue;
            }
            taskExecutor.execute(() -> {
                try {
                    QuickAnalysisResult r = quickService.quickAnalyze(code, OPERATE_TYPE, true);
                    printResult(r);
                    tryTrade(r);
                } catch (Exception e) {
                    log.error("[多Agent监控] {} 分析异常", code, e);
                }
            });
        }
    }

    /**
     * 依据分析决策触发远程交易：BUY->买入，SELL->卖出；其余不操作。
     *
     * <p>发送交易请求与写入统计表并行执行（异步、不阻塞），主流程不等待二者完成。</p>
     */
    private void tryTrade(QuickAnalysisResult r) {
        String decision = r.getDecision();
        if (!"BUY".equals(decision) && !"SELL".equals(decision)) {
            return;
        }
        String pureCode = r.getStockCode().contains(".")
                ? r.getStockCode().split("\\.")[0] : r.getStockCode();
        String operate = "BUY".equals(decision) ? "buySome" : "sell";
        List<Map<String, Object>> tradeList = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("strategy_no", TRADE_STRATEGY_NO);
        item.put("code", pureCode);
        item.put("name", r.getStockName());
        item.put("operate", operate);
        tradeList.add(item);

        // 1. 并行任务一：发送交易请求（异步，不等待）
        taskExecutor.execute(() -> {
            try {
                Object res = stockTradeService.tradeAsMonitor(tradeList);
                log.info("[多Agent监控] 触发交易 {} {} {} -> {}", r.getStockCode(),
                        operate, r.getDecisionLabel(), res);
            } catch (Exception e) {
                log.error("[多Agent监控] 触发交易失败 {} {}: {}", r.getStockCode(), operate, e.getMessage());
            }
        });

        // 2. 并行任务二：将交易信息写入统计表（异步，不等待）
        taskExecutor.execute(() -> {
            try {
                recordTradeStat(r, pureCode, operate);
            } catch (Exception e) {
                log.error("[多Agent监控] 写入交易统计失败 {} {}: {}", r.getStockCode(), operate, e.getMessage());
            }
        });
    }

    /**
     * 将当次交易信息写入 AI 实时交易统计表：BUY 初始状态为空（待确认），SELL 初始状态为「已卖出」。
     */
    private void recordTradeStat(QuickAnalysisResult r, String pureCode, String operate) {
        boolean isBuy = "buySome".equals(operate);
        StockAiTradeStat stat = new StockAiTradeStat();
        stat.setId(UUID.randomUUID().toString());
        stat.setStockCode(StockCodeUtils.formatStockCode(pureCode));
        stat.setStockName(r.getStockName());
        stat.setTradeType(isBuy ? "BUY" : "SELL");
        stat.setStatus(isBuy ? "" : "已卖出");
        stat.setTradeDate(LocalDate.now());
        stat.setStrategyNo(TRADE_STRATEGY_NO);
        stat.setReasoning(r.getReasoning());
        // 当前价格（获取失败不影响主流程）
        try {
            StockRealTimePriceDTO priceDTO = stockDataUtils.getRealTimePrice(pureCode);
            if (priceDTO != null && priceDTO.getP() != null) {
                stat.setPrice(new BigDecimal(priceDTO.getP().toString()));
            }
        } catch (Exception e) {
            log.warn("[多Agent监控] 获取实时价格失败 {}: {}", pureCode, e.getMessage());
        }
        stat.setCreateTime(LocalDateTime.now());
        stat.setUpdateTime(LocalDateTime.now());
        stockAiTradeStatService.insertStockAiTradeStat(stat);
    }

    /**
     * 控制台打印简要结果。
     */
    private void printResult(QuickAnalysisResult r) {
        String line = String.format(
                "[多Agent监控结果] %s(%s) | 类型:%s | 决策:%s | 仓位:%d%% | 降级:%s | 耗时:%dms | 理由:%s",
                r.getStockCode(), r.getStockName(), r.getOperateTypeName(),
                r.getDecisionLabel(), r.getPositionPct(), r.isDegraded() ? "是" : "否",
                r.getCostMs(), r.getReasoning());
        System.out.println(line);
        log.info(line);
    }
}