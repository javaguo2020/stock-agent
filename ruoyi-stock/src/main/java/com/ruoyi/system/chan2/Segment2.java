package com.ruoyi.system.chan2;

import java.util.List;

/**
 * 线段。
 *
 * 线段至少由三笔组成，且前三笔必须有重叠部分。
 * 线段方向由组成线段的笔的方向决定；线段被破坏（结束）由特征序列分型确认。
 * 线段本质上是次级别走势在本级别的映射。
 */
public class Segment2 implements Chan2Item {

    /** 线段方向："up" / "down" */
    public String direction;

    /** 起始分型 */
    public Fractal2 start;

    /** 结束分型 */
    public Fractal2 end;

    /** 线段覆盖的最高价 */
    public double high;

    /** 线段覆盖的最低价 */
    public double low;

    /** 组成线段的笔数量（>=3） */
    public int biCount;

    /** 组成线段的笔序列 */
    public List<Bi2> bis;

    /** 是否未完成（最后一个线段延伸到当前最新笔） */
    public boolean open;

    public Segment2() {
    }

    public Segment2(String direction, Fractal2 start, Fractal2 end, int biCount,
                    List<Bi2> bis, boolean open) {
        this.direction = direction;
        this.start = start;
        this.end = end;
        this.biCount = biCount;
        this.bis = bis;
        this.open = open;
        double h = Double.NEGATIVE_INFINITY;
        double l = Double.POSITIVE_INFINITY;
        for (Bi2 b : bis) {
            h = Math.max(h, b.high);
            l = Math.min(l, b.low);
        }
        this.high = h;
        this.low = l;
    }

    @Override
    public double high() {
        return high;
    }

    @Override
    public double low() {
        return low;
    }

    @Override
    public String startDate() {
        return start.date;
    }

    @Override
    public String endDate() {
        return end.date;
    }

    @Override
    public int startIndex() {
        return start.index;
    }

    @Override
    public int endIndex() {
        return end.index;
    }
}
