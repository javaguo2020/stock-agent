package com.ruoyi.system.agent.enums;

/**
 * 分析流程节点（状态机）：按枚举声明顺序依次执行。
 */
public enum AnalysisNode {

    DATA_FETCH("数据拉取"),
    CHAN("缠论分析"),
    TECH("技术指标分析"),
    PATTERN("形态分析"),
    FUNDAMENTAL("基本面舆情分析"),
    DEBATE("多空辩论"),
    JUDGE("法官裁决");

    private final String label;

    AnalysisNode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
