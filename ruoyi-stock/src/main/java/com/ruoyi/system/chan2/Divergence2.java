package com.ruoyi.system.chan2;

/**
 * 背驰判断结果。
 *
 * 背驰是走势力度衰竭的信号，是判断第一类转折的最大标志。
 * 判断方法：比较相邻两段同向走势的力度（借助 MACD），
 * 当价格创出新高/新低，但对应力度指标反而减弱时，即为背驰。
 */
public class Divergence2 {

    /** 背驰类型："top" 顶背驰 / "bottom" 底背驰 */
    public String kind;

    /** 背驰发生的分型位置（顶背驰为顶分型，底背驰为底分型） */
    public Fractal2 fractal;

    /** 背驰细节说明（DIF 峰值 / 柱面积对比） */
    public String detail;

    public Divergence2() {
    }

    public Divergence2(String kind, Fractal2 fractal, String detail) {
        this.kind = kind;
        this.fractal = fractal;
        this.detail = detail;
    }
}
