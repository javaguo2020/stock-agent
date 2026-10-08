package com.ruoyi.system.agent.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * 仓位管理委员的仓位建议。
 */
@Data
public class PositionAdvice implements Serializable {

    /** 建议仓位百分比（0-80，10的整数倍） */
    private int positionPct;
    /** 基于证据可信度评估的向上概率（0-100），仓位算法核心输入 */
    private int upProbability;
    /** 波动率说明（基于 ATR） */
    private String volatilityNote;
    /** 建议理由 */
    private String reason;
}
