package com.ruoyi.system.chan2;

/**
 * 走势类型。
 *
 *  - 盘整：某完成的走势类型中只包含一个走势中枢
 *  - 上涨/下跌：某完成的走势类型中至少包含两个及以上依次同向的走势中枢
 *  - 未形成：尚未形成有效中枢
 */
public enum TrendType {

    UP("上涨"),
    DOWN("下跌"),
    CONSOLIDATION("盘整"),
    UNDEFINED("未形成");

    private final String label;

    TrendType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
