package com.ruoyi.system.chan2;

/**
 * 中枢。
 *
 * 定义：某级别走势类型中，至少三个连续次级别走势类型所重叠的部分。
 *  - 中枢顶 ZG = 每段走势高点中的最低点
 *  - 中枢底 ZD = 每段走势低点中的最高点
 *  - 有效中枢要求 ZG > ZD
 */
public class Pivot2 {

    /** 中枢顶（上沿）= min(前三段高点) */
    private double zg;

    /** 中枢底（下沿）= max(前三段低点) */
    private double zd;

    /** "bi" 笔中枢(次级别) / "segment" 线段中枢(本级别) */
    private String level;

    /** 中枢起始日期 */
    private String startDate;

    /** 中枢结束日期 */
    private String endDate;

    /** 构成中枢的段数量（>=3，含延伸段） */
    private int segments;

    /** 中枢起始分型在合并K线中的下标 */
    private int startIndex;

    /** 中枢结束分型在合并K线中的下标 */
    private int endIndex;

    public Pivot2() {
    }

    public Pivot2(double zg, double zd, String level, String startDate, String endDate,
                  int segments, int startIndex, int endIndex) {
        this.zg = zg;
        this.zd = zd;
        this.level = level;
        this.startDate = startDate;
        this.endDate = endDate;
        this.segments = segments;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
    }

    public double getZg() {
        return zg;
    }

    public void setZg(double zg) {
        this.zg = zg;
    }

    public double getZd() {
        return zd;
    }

    public void setZd(double zd) {
        this.zd = zd;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public int getSegments() {
        return segments;
    }

    public void setSegments(int segments) {
        this.segments = segments;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public void setStartIndex(int startIndex) {
        this.startIndex = startIndex;
    }

    public int getEndIndex() {
        return endIndex;
    }

    public void setEndIndex(int endIndex) {
        this.endIndex = endIndex;
    }
}
