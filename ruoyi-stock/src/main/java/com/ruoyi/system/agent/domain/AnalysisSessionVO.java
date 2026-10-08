package com.ruoyi.system.agent.domain;

import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话状态 VO（前端轮询返回，剔除体积庞大的原始数据上下文）。
 */
@Data
public class AnalysisSessionVO implements Serializable {

    private String sessionId;
    private String stockCode;
    private String stockName;
    private Integer operateType;
    private String operateTypeName;

    /** RUNNING/INTERRUPTED/DONE/FAILED */
    private String status;
    /** 状态中文 */
    private String statusLabel;
    /** 当前（或中断待执行）节点 */
    private String currentNode;
    /** 节点中文名 */
    private String currentNodeLabel;
    /** 中断/失败原因 */
    private String failReason;

    /** 节点进度：节点名 -> PENDING/RUNNING/SUCCESS */
    private Map<String, String> nodeStatus = new LinkedHashMap<>();
    /** 完成节点数 / 总节点数，如 "3/7" */
    private String progress;

    private List<EvidenceCard> evidenceCards;
    private List<DebateRound> debateTranscript;
    private PositionAdvice positionAdvice;
    private JudgeVerdict verdict;

    private long createTime;
    private long updateTime;
}
