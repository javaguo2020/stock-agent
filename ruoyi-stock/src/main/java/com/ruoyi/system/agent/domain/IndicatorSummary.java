package com.ruoyi.system.agent.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * 单级别 K 线的技术指标摘要（本地计算，供子 Agent 与仓位委员使用）。
 */
@Data
public class IndicatorSummary implements Serializable {

    /** K 线级别：5/15/30/60/d/w/m */
    private String level;
    /** 级别名称：5分钟/日线等 */
    private String levelName;
    /** K 线条数 */
    private int bars;
    /** 最新一根 K 线时间 */
    private String lastDate;
    /** 最新收盘价 */
    private double lastClose;
    /** 最新涨跌幅 % */
    private double changePct;

    /** 均线值 */
    private double ma5;
    private double ma10;
    private double ma20;
    private double ma60;
    /** 均线形态：多头排列/空头排列/均线粘合/多空交织 */
    private String maPattern;

    /** MACD */
    private double dif;
    private double dea;
    private double hist;
    /** MACD 信号：如 金叉(3根前)/死叉(5根前)/DIF在DEA上方运行/DIF在DEA下方运行 */
    private String macdSignal;

    /** KDJ */
    private double kdjK;
    private double kdjD;
    private double kdjJ;
    /** KDJ 信号：超买/超卖/金叉/死叉/中性 */
    private String kdjSignal;

    /** BOLL */
    private double bollUp;
    private double bollMid;
    private double bollLow;
    /** 收盘价在布林带中的位置百分比（0=下轨，100=上轨） */
    private double bollPosPct;
    /** 布林带宽度占中轨百分比（衡量收口/开口） */
    private double bollBandwidthPct;
    /** BOLL 信号：突破上轨/跌破下轨/轨道收口/中轨上方/中轨下方 */
    private String bollSignal;

    /** 量比（最新量 / 前5根均量） */
    private double volRatio;
    /** 量能信号：显著放量/放量/平量/缩量 */
    private String volumeSignal;

    /** ATR(14) */
    private double atr;
    /** ATR 占收盘价百分比（波动率） */
    private double atrPct;
}
