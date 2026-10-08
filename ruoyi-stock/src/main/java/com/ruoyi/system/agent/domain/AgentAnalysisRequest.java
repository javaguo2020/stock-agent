package com.ruoyi.system.agent.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * 多 Agent 分析请求。
 */
@Data
public class AgentAnalysisRequest implements Serializable {

    /** 股票代码，支持 000001 / 000001.SZ 两种格式 */
    private String stockCode;

    /** 操作类型：1=短期、2=中长期、3=长期 */
    private Integer operateType;

    /** 风险偏好：1=保守、2=稳健、3=激进（仓位管理委员使用，默认稳健） */
    private Integer riskPreference;

    /**
     * 监控模式标记（实时监控定时任务使用）：true 时数据采集仅保留 15 分钟 + 日线，
     * 减少 K 线数量节约 Token，并在子 Agent 提示中标注"短线交易"。页面全量分析不开启。
     */
    private boolean monitorMode;
}
