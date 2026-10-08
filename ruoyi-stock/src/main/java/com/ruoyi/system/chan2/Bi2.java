package com.ruoyi.system.chan2;

/**
 * 笔。
 *
 * 定义：两个相邻的顶分型和底分型之间的连线称为一笔。
 *  - 上升笔 = 底分型 + 上升K线 + 顶分型
 *  - 下降笔 = 顶分型 + 下降K线 + 底分型
 *  - 顶底分型之间至少有一根独立K线（minGap）
 *  - 顶分型与底分型不能共用K线
 */
public class Bi2 implements Chan2Item {

    /** 笔方向："up" 上升笔 / "down" 下降笔 */
    public String direction;

    /** 起始分型（上升笔为底分型，下降笔为顶分型） */
    public Fractal2 start;

    /** 结束分型（上升笔为顶分型，下降笔为底分型） */
    public Fractal2 end;

    /** 整笔的最高价 */
    public double high;

    /** 整笔的最低价 */
    public double low;

    public Bi2() {
    }

    public Bi2(String direction, Fractal2 start, Fractal2 end) {
        this.direction = direction;
        this.start = start;
        this.end = end;
        this.high = Math.max(start.high, end.high);
        this.low = Math.min(start.low, end.low);
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
