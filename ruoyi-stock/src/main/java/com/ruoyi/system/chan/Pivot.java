package com.ruoyi.system.chan;

import lombok.Data;

/**
 * 中枢。对应 Python chan.py 的 Pivot dataclass。
 */
@Data
public class Pivot {
    /** 中枢顶（上沿）= min(前三段高点) */
    private double zg;
    /** 中枢底（下沿）= max(前三段低点) */
    private double zd;
    /** "bi" 笔中枢(次级别) / "segment" 线段中枢(本级别) */
    private String level;
    private String startDate;
    private String endDate;
    /** 构成中枢的段数量（>=3） */
    private int segments;
    private int startIndex;
    private int endIndex;

    public Pivot() {
    }

    public Pivot(double zg, double zd, String level, String startDate, String endDate,
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
}
