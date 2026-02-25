package com.kimiha.vortexcore.model;

import lombok.Getter;

/**
 * 订单合法性校验结果，不依赖异常传递失败信息。
 */
@Getter
public class ValidationResult {
    private final boolean success;
    private final String message;

    private ValidationResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static ValidationResult pass() {
        return new ValidationResult(true, null);
    }

    public static ValidationResult fail(String message) {
        return new ValidationResult(false, message);
    }
}
