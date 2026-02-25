package com.kimiha.vortexcore.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 下单处理结果：包含校验结果与订单（校验通过时已发布到撮合队列）
 */
@Getter
@AllArgsConstructor
public class ProcessOrderResult {
    private final ValidationResult validation;
    private final OrderEntity order;

    public boolean isSuccess() {
        return validation.isSuccess();
    }

    public String getErrorMessage() {
        return validation.getMessage();
    }
}
