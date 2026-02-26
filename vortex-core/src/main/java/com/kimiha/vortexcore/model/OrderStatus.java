package com.kimiha.vortexcore.model;

/**
 * 订单状态：用于状态机与持久化
 */
public enum OrderStatus {
    /** 已接受、在簿挂单且未发生任何成交 */
    New,
    /** 部分成交，仍有剩余在簿或刚成交完当笔 */
    PartiallyFilled,
    /** 全部成交 */
    Filled,
    /** 已撤单（全部或部分撤） */
    Canceled,
    /** 下单时被拒（重复单、对敲等），不入簿 */
    Rejected
}
