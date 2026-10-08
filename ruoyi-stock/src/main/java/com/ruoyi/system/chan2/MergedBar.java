package com.ruoyi.system.chan2;

/**
 * 合并后的K线（完成包含关系处理后）。
 *
 * 包含关系处理：相邻两根K线若价格区间有重叠，则按趋势方向合并。
 * 每根合并K线记录其覆盖的原始K线下标范围 [origStart, origEnd]，
 * 用于将分型/笔/线段的位置映射回原始K线，以对齐 MACD 等指标序列。
 */
public class MergedBar {

    /** 开盘价 */
    public double open;

    /** 最高价（合并后的取值） */
    public double high;

    /** 最低价（合并后的取值） */
    public double low;

    /** 收盘价（合并后取最新一根） */
    public double close;

    /** 成交量（合并后累加） */
    public double volume;

    /** 该合并K线覆盖的最后日期 */
    public String date;

    /** 覆盖的原始K线起始下标（含） */
    public int origStart;

    /** 覆盖的原始K线结束下标（含） */
    public int origEnd;

    /** 相对前一根合并K线的方向："up" / "down"，用于后续包含处理 */
    public String direction;

    public MergedBar() {
    }
}
