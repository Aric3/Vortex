package com.kimiha.vortexcore.model;

public enum ResultCode {
    SUCCESS(0),
    VALIDATION_ERROR(1999),
    NOT_FOUND(1001),
    SYSTEM_ERROR(5000),
    WASH_TRADE_REJECT(4001),
    DUPLICATE_CL_ORDER_ID(4002),
    PRICE_DEVIATION_REJECT(4003);

    private final int code;

    ResultCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
