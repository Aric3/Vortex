package com.kimiha.vortexcore.matching;

/** 单笔撮合成交结果，不可变。 */
public record TradeResult(
        String takerClOrderId,
        String makerClOrderId,
        Double price,
        int qty,
        String securityId,
        String makerSide,
        String makerShareholderId,
        int makerOriginalQty,
        Double makerPrice
) {
}
