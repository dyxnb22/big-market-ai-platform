package com.dyx.market.types.enums;

/** 用于待处理/对账流程的远程写 RPC 结果分类。 */
public enum RemoteWriteOutcome {
    /** 远程写已明确成功。 */
    SUCCESS,
    /** 远程写已明确被业务拒绝，通常可以结束或回滚本地意图。 */
    REJECTED,
    /** 远程写结果未知，可能已提交，必须保留记录等待确认。 */
    UNKNOWN
}
