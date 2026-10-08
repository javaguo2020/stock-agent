package com.ruoyi.system.chan2;

/**
 * MACD 指标结果。
 *
 * EMA 递推公式与 pandas ewm(adjust=False) 一致：
 *   y[0] = x[0], y[i] = alpha*x[i] + (1-alpha)*y[i-1], alpha = 2/(span+1)
 * DIF = EMA(close, fast) - EMA(close, slow)
 * DEA = EMA(DIF, signal)
 * MACD柱 = 2 * (DIF - DEA)
 */
public class Macd2 {

    /** DIF 序列 */
    public double[] dif;

    /** DEA 序列 */
    public double[] dea;

    /** MACD 柱序列 = 2 * (DIF - DEA) */
    public double[] hist;

    public Macd2(double[] dif, double[] dea, double[] hist) {
        this.dif = dif;
        this.dea = dea;
        this.hist = hist;
    }
}
