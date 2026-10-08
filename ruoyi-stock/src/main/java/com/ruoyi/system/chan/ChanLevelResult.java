package com.ruoyi.system.chan;

import com.ruoyi.system.chan2.BuySellPoint;
import lombok.Data;

import java.util.List;

/**
 * 单级别缠论结论（含动量统计）。对应 Python run.py fetch_and_analyze() 的输出字典。
 * 该对象同时作为 ChanAdvisor 生成操作建议的输入。
 */
@Data
public class ChanLevelResult {
    /** 级别键：5/15/30/60/d/w/m/y */
    private String level;
    /** 中文名：5分钟/日线/周线等 */
    private String name;
    /** K线周期：5min/daily/weekly 等 */
    private String period;
    /** 中枢震荡 / 趋势 / 数据不足 / 取数失败 */
    private String state;
    private String trendDirection;
    private String pivotLevel;
    private Double pivotTopZg;
    private Double pivotBottomZd;
    private String pivotSpanStart;
    private String pivotSpanEnd;
    private Boolean divergenceTop;
    private Boolean divergenceBottom;
    private List<String> divergenceDetail;
    private String error;
    private Integer bars;

    // ---- 动量统计（供操作建议使用）----
    private Double lastClose;
    private Double prevClose;
    private Double dayHigh;
    private Double dayLow;
    private Double dayChangePct;
    private Double recentHigh20;
    private Double recentLow20;
    private Double volRatio;

    // ---- 分析详情 ----
    private String lastDate;
    private Double dif;
    private Double dea;
    private Double hist;
    private Integer mergedKlines;
    private Integer fractals;
    private Integer biCount;
    private Integer segmentCount;
    private Integer biPivotsCount;
    private Integer segmentPivotsCount;

    // ---- 中枢明细（供前端画图/列表展示）----
    private List<Pivot> biPivots;
    private List<Pivot> segmentPivots;

    // ---- 第二套缠论逻辑(v2)扩展字段 ----
    /** 走势类型：上涨 / 下跌 / 盘整 / 未形成（仅 v2 返回） */
    private String trendType;
    /** 三类买卖点列表（仅 v2 返回） */
    private List<BuySellPoint> buySellPoints;
}
