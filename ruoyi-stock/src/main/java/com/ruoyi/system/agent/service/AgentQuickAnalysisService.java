package com.ruoyi.system.agent.service;

import com.ruoyi.system.agent.config.AgentAnalysisProperties;
import com.ruoyi.system.agent.domain.AgentAnalysisRequest;
import com.ruoyi.system.agent.domain.AnalysisSession;
import com.ruoyi.system.agent.domain.JudgeVerdict;
import com.ruoyi.system.agent.domain.QuickAnalysisResult;
import com.ruoyi.system.agent.domain.StockDataContext;
import com.ruoyi.system.agent.enums.AnalysisNode;
import com.ruoyi.system.agent.enums.SessionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 方法调用（实时监控）模式：同步阻塞 + 熔断降级。
 *
 * <p>不保存任何中间状态（节约内存与 Token）；整体耗时超过阈值直接熔断，
 * 返回默认"继续持有"，保证实时监控链路稳定。</p>
 */
@Slf4j
@Service
public class AgentQuickAnalysisService {

    @Autowired
    private AgentFlowExecutor flowExecutor;

    @Autowired
    private AgentAnalysisProperties properties;

    private final ExecutorService workerPool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "agent-quick-" + SEQ.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    /** 正在分析的唯一键（code|operateType），防止重复占用线程与 Token */
    private final java.util.Set<String> runningKeys = ConcurrentHashMap.newKeySet();

    /**
     * 同步执行完整多 Agent 分析，返回简要结果；超时/异常熔断为默认"继续持有"。
     */
    public QuickAnalysisResult quickAnalyze(String stockCode, int operateType) {
        return quickAnalyze(stockCode, operateType, false);
    }

    /**
     * 同步执行完整多 Agent 分析（可指定监控模式）；超时/异常熔断为默认"继续持有"。
     *
     * @param stockCode   股票代码
     * @param operateType 操作类型
     * @param monitorMode true 表示实时监控短线模式（数据采集仅 15分钟+日线，子Agent标注短线交易）
     */
    public QuickAnalysisResult quickAnalyze(String stockCode, int operateType, boolean monitorMode) {
        long start = System.currentTimeMillis();
        String key = stockCode + "|" + operateType + "|" + monitorMode;
        if (!runningKeys.add(key)) {
            QuickAnalysisResult r = degraded(stockCode, operateType, "该股票正在分析中，本次触发跳过", start);
            r.setOperateTypeName(operateTypeName(operateType));
            return r;
        }
        Future<QuickAnalysisResult> future = workerPool.submit(() -> runQuick(stockCode, operateType, monitorMode, start));
        try {
            return future.get(properties.getOverallTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("快速分析整体超时熔断: {} operateType={} 阈值{}秒", stockCode, operateType,
                    properties.getOverallTimeoutSeconds());
            return degraded(stockCode, operateType, "整体分析耗时超过熔断阈值，按保守策略默认继续持有", start);
        } catch (Exception e) {
            log.warn("快速分析异常降级: {} {}", stockCode, e.getMessage());
            return degraded(stockCode, operateType, "分析异常(" + e.getMessage() + ")，按保守策略默认继续持有", start);
        } finally {
            runningKeys.remove(key);
        }
    }

    /**
     * 是否正在分析（供监控任务查询）。
     */
    public boolean isRunning(String stockCode, int operateType) {
        return runningKeys.contains(stockCode + "|" + operateType + "|false")
                || runningKeys.contains(stockCode + "|" + operateType + "|true");
    }

    /**
     * 纯内存执行完整流程（无检查点、无 Redis）。
     */
    private QuickAnalysisResult runQuick(String stockCode, int operateType, boolean monitorMode, long start) {
        AgentAnalysisRequest request = new AgentAnalysisRequest();
        request.setStockCode(stockCode);
        request.setOperateType(operateType);
        request.setMonitorMode(monitorMode);

        AnalysisSession session = new AnalysisSession();
        session.setSessionId("quick-" + start);
        session.setRequest(request);
        session.setStatus(SessionStatus.RUNNING.name());
        for (AnalysisNode node : AnalysisNode.values()) {
            session.getNodeStatus().put(node.name(), "RUNNING");
            flowExecutor.executeNode(session, node); // 异常上抛，由外层熔断/降级
            session.getNodeStatus().put(node.name(), "SUCCESS");
        }
        session.setStatus(SessionStatus.DONE.name());

        QuickAnalysisResult r = new QuickAnalysisResult();
        StockDataContext ctx = session.getDataContext();
        r.setStockCode(ctx.getStockCode());
        r.setStockName(ctx.getStockName());
        r.setOperateTypeName(ctx.getOperateTypeName());
        JudgeVerdict v = session.getVerdict();
        if (v != null) {
            r.setDecision(v.getDecision());
            r.setDecisionLabel(v.getDecisionLabel());
            r.setReasoning(v.getReasoning() == null ? "" : v.getReasoning());
            r.setPositionPct(v.getPositionPct());
        } else {
            r.setDecision("WATCH");
            r.setDecisionLabel("观望");
            r.setReasoning("未产出终裁结果");
        }
        r.setDegraded(false);
        r.setCostMs(System.currentTimeMillis() - start);
        log.info("快速分析完成: {}({}) {} -> {} 仓位{}% 耗时{}ms",
                r.getStockCode(), r.getStockName(), r.getDecisionLabel(), r.getDecision(),
                r.getPositionPct(), r.getCostMs());
        return r;
    }

    private QuickAnalysisResult degraded(String stockCode, int operateType, String reason, long start) {
        QuickAnalysisResult r = new QuickAnalysisResult();
        r.setStockCode(stockCode);
        r.setOperateTypeName(operateTypeName(operateType));
        r.setDecision("HOLD");
        r.setDecisionLabel("继续持有");
        r.setReasoning(reason);
        r.setPositionPct(0);
        r.setDegraded(true);
        r.setCostMs(System.currentTimeMillis() - start);
        return r;
    }

    private String operateTypeName(int operateType) {
        switch (operateType) {
            case 2: return "中长期";
            case 3: return "长期";
            default: return "短期";
        }
    }

    @PreDestroy
    void shutdown() {
        workerPool.shutdownNow();
    }
}
