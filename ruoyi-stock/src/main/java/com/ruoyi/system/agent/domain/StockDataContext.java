package com.ruoyi.system.agent.domain;

import com.ruoyi.system.domain.StockFinancialIndexDTO;
import com.ruoyi.system.domain.StockIndexMinDTO;
import com.ruoyi.system.domain.StockKlineDTO;
import com.ruoyi.system.domain.StockRealTimePriceDTO;
import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多 Agent 分析的数据上下文：数据拉取节点的产物，供后续所有节点复用（省 Token、省接口调用）。
 */
@Data
public class StockDataContext implements Serializable {

    /** 归一化后的股票代码（如 000001.SZ） */
    private String stockCode;
    /** 股票名称 */
    private String stockName;
    /** 操作类型编码 1/2/3 */
    private int operateType;
    /** 操作类型名称 */
    private String operateTypeName;
    /** 用户风险偏好：1=保守、2=稳健、3=激进（仓位评估用） */
    private int riskPreference = 2;
    /** 所属市场说明（沪市主板/深市主板/创业板/科创板/北交所） */
    private String market;
    /** 涨跌停幅度 %（主板10、创业板/科创板20、北交所30） */
    private double limitPct;
    /** 采集时间（yyyy-MM-dd HH:mm:ss） */
    private String collectTime;

    /** 个股 K 线：级别(5/15/30/60/d/w/m) -> K线列表（升序） */
    private Map<String, List<StockKlineDTO>> stockKlines = new LinkedHashMap<>();
    /** 各级别技术指标摘要：级别 -> 摘要 */
    private Map<String, IndicatorSummary> indicators = new LinkedHashMap<>();
    /** 指数 K 线：指数名+级别（如 上证指数@d） -> K线列表 */
    private Map<String, List<StockIndexMinDTO>> indexKlines = new LinkedHashMap<>();
    /** 指数摘要文本：指数名(级别) -> 一句话摘要 */
    private Map<String, String> indexSummaries = new LinkedHashMap<>();

    /** 财务主要指标（最近几期，升序） */
    private List<StockFinancialIndexDTO> financials;
    /** 实时行情（含 PE/PB/换手率/最新价） */
    private StockRealTimePriceDTO realtime;
    /** 涨跌停统计 */
    private LimitUpStats limitUpStats = new LimitUpStats();
    /** 日线 K 线压缩文本（形态分析用，最近约80根） */
    private String dailyKlineDigest;

    /** 日线 ATR（仓位管理委员用） */
    private double atr;
    /** 日线 ATR 百分比（波动率） */
    private double atrPct;

    /** 短线监控模式（多Agent实时监控定时任务）：子Agent应关注 15 分钟与日线级别短期走势 */
    private boolean shortTermMonitor;

    /**
     * 涨跌停表现统计（基于历史日 K 线 + 当日实时行情计算）。
     */
    @Data
    public static class LimitUpStats implements Serializable {
        /** 近120日涨停次数 */
        private int limitUpCount120;
        /** 近20日涨停次数 */
        private int limitDownCount120;
        /** 近20日涨停次数 */
        private int limitUpCount20;
        /** 最近一次涨停日期 */
        private String lastLimitUpDate;
        /** 最近一次跌停日期 */
        private String lastLimitDownDate;
        /** 当前连板数（从最新一根K线往前连续涨停） */
        private int currentStreak;
        /** 今日状态：涨停/跌停/正常 */
        private String todayStatus;
    }
}
