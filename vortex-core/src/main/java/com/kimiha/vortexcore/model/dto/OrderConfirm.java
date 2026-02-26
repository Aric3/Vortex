package com.kimiha.vortexcore.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderConfirm(
        String clOrderId,
        String market,
        String securityId,
        String side,
        Integer qty,
        Double price,
        String shareholderId
) {
    public static final ReportType REPORT_TYPE = ReportType.ORDER_CONFIRM;
}
