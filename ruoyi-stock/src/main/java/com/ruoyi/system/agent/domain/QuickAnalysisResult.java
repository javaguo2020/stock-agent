package com.ruoyi.system.agent.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * 方法调用（实时监控）模式的简要分析结果。
 */
@Data
public class QuickAnalysisResult implements Serializable {

    /** 股票代码 */
    private String stockCode;
    /** 股票名称 */
    private String stockName;
    /** 操作类型名称 */
    private String operateTypeName;
    /** 决策：BUY/SELL/HOLD/WATCH */
    private String decision;
    /** 决策中文 */
    private String decisionLabel;
    /** 简要理由 */
    private String reasoning;
    /** 建议仓位 */
    private int positionPct;
    /** 是否为熔断降级的默认结果 */
    private boolean degraded;
    /** 分析耗时毫秒 */
    private long costMs;
}
