package com.ruoyi.system.agent.domain;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 子 Agent 输出的标准化"证据卡"：只含客观事实，不含主观多空结论。
 */
@Data
public class EvidenceCard implements Serializable {

    /** Agent 标识：缠论/技术/形态/基本面 */
    private String agent;
    /** Agent 中文名称 */
    private String agentName;
    /** 一句话客观概述 */
    private String summary;
    /** 证据条目 */
    private List<EvidenceItem> evidence;

    /**
     * 单条证据。
     */
    @Data
    public static class EvidenceItem implements Serializable {
        /** 证据标题（如：日线顶背驰） */
        private String item;
        /** 证据详情 */
        private String detail;
        /** 信号强度：强/中/弱 */
        private String strength;
        /** 置信度（0-100）：该证据事实成立的确信程度。缠论证据因代码判断不够准确，程序会额外折损 */
        private Integer confidence;
        /** 证据客观信号方向：多/空/中性（仅标注信号偏向，非买卖建议） */
        private String direction;
        /** 出处（级别或数据来源，如：日线 / 60分钟 / 财报 / 舆情） */
        private String source;
    }
}
