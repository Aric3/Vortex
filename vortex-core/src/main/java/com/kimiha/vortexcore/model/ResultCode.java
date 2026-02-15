package com.kimiha.vortexcore.model;

public enum ResultCode {
    SUCCESS(0),
    VALIDATION_ERROR(1000),
    NOT_FOUND(1001),
    SYSTEM_ERROR(5000);

    private final int code;

    ResultCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
