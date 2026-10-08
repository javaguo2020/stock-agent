package com.ruoyi.system.agent.enums;

/**
 * 分析会话状态。
 */
public enum SessionStatus {

    RUNNING("执行中"),
    INTERRUPTED("分析中断"),
    DONE("已完成"),
    FAILED("失败");

    private final String label;

    SessionStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
