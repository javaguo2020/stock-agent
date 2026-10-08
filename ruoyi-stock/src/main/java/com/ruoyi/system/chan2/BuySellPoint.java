package com.ruoyi.system.chan2;

/**
 * 买卖点（三类买卖点综合判定输出）。
 *
 * 记录买卖点的类型、位置（分型）、价格和说明，供前端列表展示。
 */
public class BuySellPoint {

    /** 类型编码："BUY1"/"SELL1"/"BUY2"/"SELL2"/"BUY3"/"SELL3" */
    private String type;

    /** 类型中文名：第一类买点 / 第三类卖点 等 */
    private String typeLabel;

    /** 方向："buy" 买点 / "sell" 卖点 */
    private String direction;

    /** 买卖点参考价格（买点取分型低点，卖点取分型高点） */
    private double price;

    /** 买卖点所在日期 */
    private String date;

    /** 买卖点分型在合并K线中的下标 */
    private int fractalIndex;

    /** 所属级别键：d / 60 / 30 等 */
    private String level;

    /** 所属级别中文名：日线 / 60分钟 等 */
    private String name;

    /** 信号说明 */
    private String description;

    public BuySellPoint() {
    }

    public BuySellPoint(String type, Fractal2 fractal, String level, String name, String description) {
        this.type = type;
        BuySellPointType t = BuySellPointType.byCode(type);
        this.typeLabel = t.getLabel();
        this.direction = t.getDirection();
        this.price = isBuy() ? fractal.low : fractal.high;
        this.date = fractal.date;
        this.fractalIndex = fractal.index;
        this.level = level;
        this.name = name;
        this.description = description;
    }

    public boolean isBuy() {
        return "buy".equals(direction);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTypeLabel() {
        return typeLabel;
    }

    public void setTypeLabel(String typeLabel) {
        this.typeLabel = typeLabel;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public int getFractalIndex() {
        return fractalIndex;
    }

    public void setFractalIndex(int fractalIndex) {
        this.fractalIndex = fractalIndex;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
