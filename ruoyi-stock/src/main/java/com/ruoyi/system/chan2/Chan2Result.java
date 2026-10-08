package com.ruoyi.system.chan2;

import java.util.ArrayList;
import java.util.List;

/**
 * 第二套缠论（v2）单级别完整分析结果。
 *
 * 包含八步处理后的全部输出：
 *   K线预处理(合并) → 分型 → 笔 → 线段 → 中枢 → 走势类型 → 背驰 → 三类买卖点。
 */
public class Chan2Result {

    /** 股票/标的代码 */
    private String symbol;

    /** K线周期，如 daily / 60min */
    private String period;

    /** 最新日期 */
    private String lastDate;

    /** 最新收盘价 */
    private Double lastClose;

    /** 当前状态：中枢震荡 / 趋势 / 数据不足 */
    private String state;

    /** 走势类型：上涨 / 下跌 / 盘整 / 未形成 */
    private String trendType;

    /** 趋势方向：向上 / 向下 */
    private String trendDirection;

    /** 当前中枢级别：本级别(线段中枢) / 次级别(笔中枢) */
    private String pivotLevel;

    /** 当前中枢顶 */
    private Double pivotTopZg;

    /** 当前中枢底 */
    private Double pivotBottomZd;

    /** 当前中枢起始日期 */
    private String pivotSpanStart;

    /** 当前中枢结束日期 */
    private String pivotSpanEnd;

    /** 当前中枢构成段数 */
    private Integer pivotSegments;

    /** 是否出现顶背驰 */
    private Boolean divergenceTop;

    /** 是否出现底背驰 */
    private Boolean divergenceBottom;

    /** 背驰细节说明列表 */
    private List<String> divergenceDetail = new ArrayList<>();

    /** 全部背驰信号（含位置） */
    private List<Divergence2> divergences = new ArrayList<>();

    /** 三类买卖点列表 */
    private List<BuySellPoint> buySellPoints = new ArrayList<>();

    /** 笔中枢列表（次级别） */
    private List<Pivot2> biPivots = new ArrayList<>();

    /** 线段中枢列表（本级别） */
    private List<Pivot2> segmentPivots = new ArrayList<>();

    /** 线段序列 */
    private List<Segment2> segments = new ArrayList<>();

    /** 笔序列 */
    private List<Bi2> bis = new ArrayList<>();

    /** 分型序列 */
    private List<Fractal2> fractals = new ArrayList<>();

    /** 合并K线序列 */
    private List<MergedBar> mergedBars = new ArrayList<>();

    /** MACD 最新值 */
    private Double dif;
    private Double dea;
    private Double hist;

    /** 结构统计 */
    private Integer mergedKlines;
    private Integer fractalsCount;
    private Integer biCount;
    private Integer segmentCount;
    private Integer biPivotsCount;
    private Integer segmentPivotsCount;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public String getLastDate() {
        return lastDate;
    }

    public void setLastDate(String lastDate) {
        this.lastDate = lastDate;
    }

    public Double getLastClose() {
        return lastClose;
    }

    public void setLastClose(Double lastClose) {
        this.lastClose = lastClose;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getTrendType() {
        return trendType;
    }

    public void setTrendType(String trendType) {
        this.trendType = trendType;
    }

    public String getTrendDirection() {
        return trendDirection;
    }

    public void setTrendDirection(String trendDirection) {
        this.trendDirection = trendDirection;
    }

    public String getPivotLevel() {
        return pivotLevel;
    }

    public void setPivotLevel(String pivotLevel) {
        this.pivotLevel = pivotLevel;
    }

    public Double getPivotTopZg() {
        return pivotTopZg;
    }

    public void setPivotTopZg(Double pivotTopZg) {
        this.pivotTopZg = pivotTopZg;
    }

    public Double getPivotBottomZd() {
        return pivotBottomZd;
    }

    public void setPivotBottomZd(Double pivotBottomZd) {
        this.pivotBottomZd = pivotBottomZd;
    }

    public String getPivotSpanStart() {
        return pivotSpanStart;
    }

    public void setPivotSpanStart(String pivotSpanStart) {
        this.pivotSpanStart = pivotSpanStart;
    }

    public String getPivotSpanEnd() {
        return pivotSpanEnd;
    }

    public void setPivotSpanEnd(String pivotSpanEnd) {
        this.pivotSpanEnd = pivotSpanEnd;
    }

    public Integer getPivotSegments() {
        return pivotSegments;
    }

    public void setPivotSegments(Integer pivotSegments) {
        this.pivotSegments = pivotSegments;
    }

    public Boolean getDivergenceTop() {
        return divergenceTop;
    }

    public void setDivergenceTop(Boolean divergenceTop) {
        this.divergenceTop = divergenceTop;
    }

    public Boolean getDivergenceBottom() {
        return divergenceBottom;
    }

    public void setDivergenceBottom(Boolean divergenceBottom) {
        this.divergenceBottom = divergenceBottom;
    }

    public List<String> getDivergenceDetail() {
        return divergenceDetail;
    }

    public void setDivergenceDetail(List<String> divergenceDetail) {
        this.divergenceDetail = divergenceDetail;
    }

    public List<Divergence2> getDivergences() {
        return divergences;
    }

    public void setDivergences(List<Divergence2> divergences) {
        this.divergences = divergences;
    }

    public List<BuySellPoint> getBuySellPoints() {
        return buySellPoints;
    }

    public void setBuySellPoints(List<BuySellPoint> buySellPoints) {
        this.buySellPoints = buySellPoints;
    }

    public List<Pivot2> getBiPivots() {
        return biPivots;
    }

    public void setBiPivots(List<Pivot2> biPivots) {
        this.biPivots = biPivots;
    }

    public List<Pivot2> getSegmentPivots() {
        return segmentPivots;
    }

    public void setSegmentPivots(List<Pivot2> segmentPivots) {
        this.segmentPivots = segmentPivots;
    }

    public List<Segment2> getSegments() {
        return segments;
    }

    public void setSegments(List<Segment2> segments) {
        this.segments = segments;
    }

    public List<Bi2> getBis() {
        return bis;
    }

    public void setBis(List<Bi2> bis) {
        this.bis = bis;
    }

    public List<Fractal2> getFractals() {
        return fractals;
    }

    public void setFractals(List<Fractal2> fractals) {
        this.fractals = fractals;
    }

    public List<MergedBar> getMergedBars() {
        return mergedBars;
    }

    public void setMergedBars(List<MergedBar> mergedBars) {
        this.mergedBars = mergedBars;
    }

    public Double getDif() {
        return dif;
    }

    public void setDif(Double dif) {
        this.dif = dif;
    }

    public Double getDea() {
        return dea;
    }

    public void setDea(Double dea) {
        this.dea = dea;
    }

    public Double getHist() {
        return hist;
    }

    public void setHist(Double hist) {
        this.hist = hist;
    }

    public Integer getMergedKlines() {
        return mergedKlines;
    }

    public void setMergedKlines(Integer mergedKlines) {
        this.mergedKlines = mergedKlines;
    }

    public Integer getFractalsCount() {
        return fractalsCount;
    }

    public void setFractalsCount(Integer fractalsCount) {
        this.fractalsCount = fractalsCount;
    }

    public Integer getBiCount() {
        return biCount;
    }

    public void setBiCount(Integer biCount) {
        this.biCount = biCount;
    }

    public Integer getSegmentCount() {
        return segmentCount;
    }

    public void setSegmentCount(Integer segmentCount) {
        this.segmentCount = segmentCount;
    }

    public Integer getBiPivotsCount() {
        return biPivotsCount;
    }

    public void setBiPivotsCount(Integer biPivotsCount) {
        this.biPivotsCount = biPivotsCount;
    }

    public Integer getSegmentPivotsCount() {
        return segmentPivotsCount;
    }

    public void setSegmentPivotsCount(Integer segmentPivotsCount) {
        this.segmentPivotsCount = segmentPivotsCount;
    }
}
