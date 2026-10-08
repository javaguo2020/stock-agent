package com.ruoyi.system.chan2;

/**
 * 三类买卖点类型枚举。
 *
 *  - 第一类买卖点：由背驰确定，是趋势转折的精确位置
 *  - 第二类买卖点：第一类买卖点之后的次低点/次高点
 *  - 第三类买点：向上突破中枢后回踩不跌破中枢上轨
 *  - 第三类卖点：向下突破中枢后反弹无法回到中枢内部
 */
public enum BuySellPointType {

    BUY1("BUY1", "第一类买点", "buy"),
    SELL1("SELL1", "第一类卖点", "sell"),
    BUY2("BUY2", "第二类买点", "buy"),
    SELL2("SELL2", "第二类卖点", "sell"),
    BUY3("BUY3", "第三类买点", "buy"),
    SELL3("SELL3", "第三类卖点", "sell");

    private final String code;
    private final String label;
    private final String direction;

    BuySellPointType(String code, String label, String direction) {
        this.code = code;
        this.label = label;
        this.direction = direction;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getDirection() {
        return direction;
    }

    public static BuySellPointType byCode(String code) {
        for (BuySellPointType t : values()) {
            if (t.code.equals(code)) {
                return t;
            }
        }
        throw new IllegalArgumentException("未知买卖点类型: " + code);
    }
}
