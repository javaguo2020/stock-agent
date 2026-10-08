package com.ruoyi.system.agent.domain;

import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 页面模式的分析会话（状态机 + 检查点）。
 *
 * <p>每完成一个节点，中间态结果序列化存入 Redis，生成唯一 analysisSessionId；
 * 遇中断时前端可凭该 id 反序列化上下文，跳过已完成节点继续执行。</p>
 */
@Data
public class AnalysisSession implements Serializable {

    /** 会话唯一ID */
    private String sessionId;
    /** 分析请求 */
    private AgentAnalysisRequest request;

    /** 会话状态：RUNNING/INTERRUPTED/DONE/FAILED */
    private String status;
    /** 当前（或中断时待执行）节点 */
    private String currentNode;
    /** 失败原因 */
    private String failReason;

    /** 节点状态：节点名 -> PENDING/RUNNING/SUCCESS/FAILED */
    private Map<String, String> nodeStatus = new LinkedHashMap<>();

    /** 数据上下文（数据拉取节点产物） */
    private StockDataContext dataContext;
    /** 四个子 Agent 的证据卡 */
    private List<EvidenceCard> evidenceCards;
    /** 辩论纪要（含仓位委员结论前的发言） */
    private List<DebateRound> debateTranscript;
    /** 仓位建议 */
    private PositionAdvice positionAdvice;
    /** 法官终裁 */
    private JudgeVerdict verdict;

    /** 创建时间戳 */
    private long createTime;
    /** 最后更新时间戳 */
    private long updateTime;

    /**
     * 会话世代号：每次恢复(resume)+1。旧世代的迟到节点结果凭此丢弃，
     * 防止覆盖恢复后新一轮执行的 Redis 检查点。
     */
    private long epoch;
}
