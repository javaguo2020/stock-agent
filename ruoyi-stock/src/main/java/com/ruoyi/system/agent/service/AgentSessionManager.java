package com.ruoyi.system.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.system.agent.config.AgentAnalysisProperties;
import com.ruoyi.system.agent.domain.AgentAnalysisRequest;
import com.ruoyi.system.agent.domain.AnalysisSession;
import com.ruoyi.system.agent.enums.AnalysisNode;
import com.ruoyi.system.agent.enums.SessionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 页面模式会话管理：状态机 + Redis 检查点 + 中断恢复。
 *
 * <p>流程拆解为 [数据拉取]->[缠论]->[技术]->[形态]->[基本面]->[多空辩论]->[法官裁决] 节点，
 * 每完成一个节点将中间态序列化存入 Redis（TTL 可配置），生成唯一 analysisSessionId；
 * 单节点超时则自动中断（状态 INTERRUPTED），前端可凭 id 一键恢复，跳过已完成节点继续执行。</p>
 *
 * <p>线程模型：会话主循环跑在项目统一异步池 taskExecutor，节点任务跑在自建节点池，
 * 两层不同池避免嵌套提交死锁。</p>
 */
@Slf4j
@Service
public class AgentSessionManager {

    private static final String KEY_PREFIX = "agent:session:";

    @Autowired
    private AgentFlowExecutor flowExecutor;

    @Autowired
    private AgentAnalysisProperties properties;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private ObjectMapper objectMapper;

    /** 项目统一异步线程池（会话主循环） */
    @Autowired
    @Qualifier("taskExecutor")
    private java.util.concurrent.Executor taskExecutor;

    /** 节点执行线程池（与会话主循环隔离，避免嵌套提交互相占满） */
    private final ExecutorService nodePool = Executors.newFixedThreadPool(4,
            new java.util.concurrent.ThreadFactory() {
                private final AtomicInteger seq = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "agent-node-" + seq.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            });

    /** 每个会话一把本地锁，保护"读-改-写"Redis 的原子性（单机部署场景） */
    private final ConcurrentHashMap<String, Object> sessionLocks = new ConcurrentHashMap<>();

    public AnalysisSession submit(AgentAnalysisRequest request) {
        AnalysisSession session = new AnalysisSession();
        session.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        session.setRequest(request);
        session.setStatus(SessionStatus.RUNNING.name());
        session.setCurrentNode(AnalysisNode.DATA_FETCH.name());
        session.setCreateTime(System.currentTimeMillis());
        session.setUpdateTime(System.currentTimeMillis());
        for (AnalysisNode node : AnalysisNode.values()) {
            session.getNodeStatus().put(node.name(), "PENDING");
        }
        save(session);
        startLoop(session);
        log.info("多Agent分析会话已提交: {} {} operateType={}", session.getSessionId(),
                request.getStockCode(), request.getOperateType());
        return session;
    }

    /**
     * 一键恢复：校验会话存在且非执行中，跳过已完成节点从中断节点继续。
     */
    public AnalysisSession resume(String sessionId) {
        AnalysisSession session = getSession(sessionId);
        if (session == null) {
            throw new RuntimeException("会话不存在或已过期: " + sessionId);
        }
        if (SessionStatus.RUNNING.name().equals(session.getStatus())) {
            return session;
        }
        if (SessionStatus.DONE.name().equals(session.getStatus())) {
            return session;
        }
        synchronized (lock(sessionId)) {
            AnalysisSession latest = getSession(sessionId);
            latest.setStatus(SessionStatus.RUNNING.name());
            latest.setFailReason(null);
            latest.setEpoch(latest.getEpoch() + 1);
            latest.setUpdateTime(System.currentTimeMillis());
            save(latest);
            session = latest;
        }
        startLoop(session);
        log.info("多Agent分析会话已恢复: {} 从节点[{}]继续", sessionId, session.getCurrentNode());
        return session;
    }

    /**
     * 查询会话（不存在返回 null）。
     */
    public AnalysisSession getSession(String sessionId) {
        try {
            String json = redisCache.getCacheObject(KEY_PREFIX + sessionId);
            if (json == null || json.isEmpty()) {
                return null;
            }
            return objectMapper.readValue(json, AnalysisSession.class);
        } catch (Exception e) {
            log.error("读取分析会话失败: {}", sessionId, e);
            return null;
        }
    }

    /**
     * 启动（或继续）会话主循环。
     */
    private void startLoop(AnalysisSession session) {
        String sessionId = session.getSessionId();
        taskExecutor.execute(() -> {
            try {
                runLoop(sessionId);
            } catch (Exception e) {
                log.error("会话主循环异常: {}", sessionId, e);
                AnalysisSession s = getSession(sessionId);
                if (s != null) {
                    markInterrupted(sessionId, s, s.getEpoch(), "会话主循环异常: " + e.getMessage());
                }
            }
        });
    }

    /**
     * 会话主循环：逐节点执行（跳过已完成），节点超时则中断。
     *
     * <p>所有状态更新都基于启动时反序列化的同一内存 session 对象（锁内 save 到 Redis），
     * 节点结果、超时中断、迟到回调之间不会互相丢失；epoch 用于丢弃"恢复后旧世代迟到结果"。</p>
     */
    private void runLoop(String sessionId) {
        AnalysisSession session = getSession(sessionId);
        if (session == null) {
            return;
        }
        final long myEpoch = session.getEpoch();
        for (AnalysisNode node : AnalysisNode.values()) {
            if ("SUCCESS".equals(session.getNodeStatus().get(node.name()))) {
                continue;
            }
            // 标记节点运行中（检查点）
            synchronized (lock(sessionId)) {
                session.setCurrentNode(node.name());
                session.getNodeStatus().put(node.name(), "RUNNING");
                session.setUpdateTime(System.currentTimeMillis());
                save(session);
            }
            Future<?> future = nodePool.submit(() -> {
                Throwable error = null;
                try {
                    flowExecutor.executeNode(session, node);
                } catch (Throwable t) {
                    error = t;
                }
                completeNode(sessionId, session, myEpoch, node, error);
            });
            try {
                future.get(nodeTimeoutSeconds(node), TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                markInterrupted(sessionId, session, myEpoch, "节点[" + node.getLabel() + "]执行超时("
                        + nodeTimeoutSeconds(node) + "秒)，已自动中断，可一键恢复");
                return;
            } catch (Exception e) {
                markInterrupted(sessionId, session, myEpoch, "节点[" + node.getLabel() + "]执行异常: " + e.getMessage());
                return;
            }
            // 节点任务结束后：失败（异常且未成功）则中断；成功则继续下一节点
            if (!"SUCCESS".equals(session.getNodeStatus().get(node.name()))) {
                return;
            }
        }
        synchronized (lock(sessionId)) {
            if (!SessionStatus.INTERRUPTED.name().equals(session.getStatus())
                    && session.getEpoch() == myEpoch) {
                session.setStatus(SessionStatus.DONE.name());
                session.setCurrentNode(null);
                session.setUpdateTime(System.currentTimeMillis());
                save(session);
                log.info("多Agent分析会话完成: {} 耗时{}秒", sessionId,
                        (session.getUpdateTime() - session.getCreateTime()) / 1000);
            }
        }
    }

    /**
     * 节点任务完成回调（节点线程内调用）：成功写检查点；异常标记中断（可恢复重跑该节点）。
     * 若会话已被恢复（epoch 变化），迟到结果直接丢弃。
     */
    private void completeNode(String sessionId, AnalysisSession session, long epoch,
                              AnalysisNode node, Throwable error) {
        synchronized (lock(sessionId)) {
            AnalysisSession latest = getSession(sessionId);
            if (latest == null || latest.getEpoch() != epoch) {
                log.info("丢弃旧世代迟到节点结果: {} {} epoch={}", sessionId, node, epoch);
                return;
            }
            if (error == null) {
                session.getNodeStatus().put(node.name(), "SUCCESS");
                session.setUpdateTime(System.currentTimeMillis());
                // 若已被超时中断，保持 INTERRUPTED 状态（结果已写入，恢复时将跳过本节点）
                save(session);
            } else {
                session.getNodeStatus().put(node.name(), "PENDING");
                session.setStatus(SessionStatus.INTERRUPTED.name());
                session.setFailReason("节点[" + node.getLabel() + "]执行失败: " + error.getMessage());
                session.setUpdateTime(System.currentTimeMillis());
                save(session);
                log.warn("节点执行失败: {} {} -> {}", sessionId, node, error.getMessage());
            }
        }
    }

    /**
     * 标记会话中断（等待人工一键恢复）。
     */
    private void markInterrupted(String sessionId, AnalysisSession session, long epoch, String reason) {
        synchronized (lock(sessionId)) {
            AnalysisSession latest = getSession(sessionId);
            if (latest == null || latest.getEpoch() != epoch
                    || SessionStatus.DONE.name().equals(session.getStatus())) {
                return;
            }
            // 仅当当前节点仍在RUNNING（未由迟到回调写入结果）时回退为PENDING，恢复时重跑
            if (session.getCurrentNode() != null
                    && "RUNNING".equals(session.getNodeStatus().get(session.getCurrentNode()))) {
                session.getNodeStatus().put(session.getCurrentNode(), "PENDING");
            }
            session.setStatus(SessionStatus.INTERRUPTED.name());
            session.setFailReason(reason);
            session.setUpdateTime(System.currentTimeMillis());
            save(session);
            log.warn("多Agent分析中断: {} 原因: {}", sessionId, reason);
        }
    }

    /**
     * 节点超时时间：DEBATE 节点包含 2×轮次+1 次大模型调用，按调用次数放大超时。
     */
    private long nodeTimeoutSeconds(AnalysisNode node) {
        long base = properties.getNodeTimeoutSeconds();
        if (node == AnalysisNode.DEBATE) {
            return base * (properties.getDebateRounds() * 2 + 1);
        }
        return base;
    }

    private void save(AnalysisSession session) {
        try {
            String json = objectMapper.writeValueAsString(session);
            redisCache.setCacheObject(KEY_PREFIX + session.getSessionId(), json,
                    (int) properties.getSessionTtlHours(), TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("保存分析会话失败: {}", session.getSessionId(), e);
        }
    }

    private Object lock(String sessionId) {
        return sessionLocks.computeIfAbsent(sessionId, k -> new Object());
    }

    @PreDestroy
    void shutdown() {
        nodePool.shutdownNow();
    }
}
