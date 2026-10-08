package com.ruoyi.system.chan2;

/**
 * 原始K线输入（未做包含关系处理）。
 *
 * 第二套缠论逻辑（v2）的输入模型。
 * 字段对齐外部行情数据：date/open/high/low/close/volume。
 */
public class Chan2Kline {

    /** 时间戳/交易日期，如 2023-08-28 或 09:31 */
    public String date;

    /** 开盘价 */
    public double open;

    /** 最高价 */
    public double high;

    /** 最低价 */
    public double low;

    /** 收盘价 */
    public double close;

    /** 成交量 */
    public double volume;

    /** 在原始K线序列中的下标（用于回溯定位） */
    public int origIndex;

    public Chan2Kline() {
    }

    public Chan2Kline(String date, double open, double high, double low, double close, double volume) {
        this.date = date;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }
}
