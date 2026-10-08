package com.ruoyi.system.agent.domain;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 多空辩论中的一轮发言。
 */
@Data
public class DebateRound implements Serializable {

    /** 轮次（从1开始） */
    private int round;
    /** 发言角色：BULL=首席多头 / BEAR=首席空头 */
    private String role;
    /** 角色名称 */
    private String roleName;
    /** 论点内容 */
    private String argument;
    /** 引用的证据卡条目（如：[缠论] 日线底背驰） */
    private List<String> citedEvidence;

    /** 首席多头 */
    public static final String ROLE_BULL = "BULL";
    /** 首席空头 */
    public static final String ROLE_BEAR = "BEAR";
}
