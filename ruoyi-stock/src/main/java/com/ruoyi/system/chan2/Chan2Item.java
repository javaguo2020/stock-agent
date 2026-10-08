package com.ruoyi.system.chan2;

/**
 * 笔/线段的公共访问接口，用于通用的中枢识别逻辑（FindPivots）。
 */
public interface Chan2Item {

    /** 该走势段的最高价 */
    double high();

    /** 该走势段的最低价 */
    double low();

    /** 起始日期（起分型日期） */
    String startDate();

    /** 结束日期（止分型日期） */
    String endDate();

    /** 起始分型在合并K线中的下标 */
    int startIndex();

    /** 结束分型在合并K线中的下标 */
    int endIndex();
}
