package com.ruoyi.system.agent.enums;

/**
 * 操作类型：1=短期、2=中长期、3=长期。
 */
public enum OperateType {

    SHORT_TERM(1, "短期"),
    MID_TERM(2, "中长期"),
    LONG_TERM(3, "长期");

    private final int code;
    private final String label;

    OperateType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static OperateType of(Integer code) {
        if (code == null) {
            return SHORT_TERM;
        }
        for (OperateType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        throw new IllegalArgumentException("未知操作类型: " + code + "，可选 1=短期 2=中长期 3=长期");
    }
}
