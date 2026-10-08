package com.ruoyi.system.agent.domain;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 法官终裁结果（赔率思维）。
 */
@Data
public class JudgeVerdict implements Serializable {

    /** 辩论胜方：BULL/BEAR/TIE */
    private String winner;
    /** 最终决策：BUY=买入 / SELL=卖出 / HOLD=继续持有 / WATCH=观望 */
    private String decision;
    /** 决策中文 */
    private String decisionLabel;
    /** 基于证据可信度评估的向上概率（0-100）：用户操作周期内股价向上运动的概率 */
    private int upProbability;
    /** 风险回报比（如 2.8 表示 1:2.8） */
    private double riskReward;
    /** 建议仓位百分比 */
    private int positionPct;
    /** 建议买入价格区间（如 12.30-12.50） */
    private String entryPrice;
    /** 止损价 */
    private double stopLoss;
    /** 目标价 */
    private double targetPrice;
    /** 裁决理由 */
    private String reasoning;
    /** 关键考量因素 */
    private List<String> keyFactors;
}
