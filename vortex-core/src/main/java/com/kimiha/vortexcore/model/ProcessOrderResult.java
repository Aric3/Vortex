package com.kimiha.vortexcore.model;

import com.kimiha.vortexcore.model.dto.OrderSubmittedDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 下单处理结果：包含校验结果与提交结果（校验通过时已入队，确认/拒绝通过 stream/reports 推送）
 */
@Getter
@AllArgsConstructor
public class ProcessOrderResult {
    private final ValidationResult validation;
    private final OrderSubmittedDto submitResult; // 已提交信息，仅含 clOrderId

    public boolean isSuccess() {
        return validation.isSuccess();
    }

    public String getErrorMessage() {
        return validation.getMessage();
    }
}
