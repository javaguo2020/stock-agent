package com.ruoyi.system.chan;

import lombok.Data;

import java.util.List;

/**
 * 操作建议结果。对应 Python advisor.py generate_advice() 的输出。
 */
@Data
public class ChanAdvice {
    private String tag;
    private List<String> holder;
    private List<String> buyer;
    private Assessment assessment;

    @Data
    public static class Assessment {
        private String shortTerm;
        private String midTerm;
        private String longTerm;
        private String overall;
    }
}
