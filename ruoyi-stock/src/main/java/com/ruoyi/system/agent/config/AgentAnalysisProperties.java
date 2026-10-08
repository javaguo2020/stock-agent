package com.ruoyi.system.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 多 Agent 分析配置（application-druid.yml 中 agent.analysis 前缀）。
 */
@Component
@ConfigurationProperties(prefix = "agent.analysis")
@Data
public class AgentAnalysisProperties {

    /** 单节点超时（秒）：页面模式下单个节点（如一次子Agent分析）超时则中断，等待人工一键恢复 */
    private long nodeTimeoutSeconds = 120;

    /** 整体熔断阈值（秒）：方法调用（实时监控）模式下整体分析超过该时长直接熔断，返回默认"继续持有" */
    private long overallTimeoutSeconds = 300;

    /** 多空辩论轮数（上限） */
    private int debateRounds = 3;

    /** 会话缓存时长（小时）：中断恢复检查点的保留时间 */
    private long sessionTtlHours = 24;
}
