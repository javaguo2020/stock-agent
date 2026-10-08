package com.ruoyi.system.chan2;

/**
 * 分型。
 *
 * 顶分型：三根相邻合并K线中，中间K线的高点是三者中最高的，低点也是三者中最高的。
 * 底分型：三根相邻合并K线中，中间K线的低点是三者中最低的，高点也是三者中最低的。
 */
public class Fractal2 {

    /** 在合并K线序列中的下标（即中间那根合并K线的下标） */
    public int index;

    /** 分型类型："top" 顶分型 / "bottom" 底分型 */
    public String kind;

    /** 分型处的高点（顶分型取中间K线高点；底分型取中间K线高点，用于区间判断） */
    public double high;

    /** 分型处的低点（底分型取中间K线低点；顶分型取中间K线低点，用于区间判断） */
    public double low;

    /** 分型所在日期 */
    public String date;

    public Fractal2() {
    }

    public Fractal2(int index, String kind, double high, double low, String date) {
        this.index = index;
        this.kind = kind;
        this.high = high;
        this.low = low;
        this.date = date;
    }
}
