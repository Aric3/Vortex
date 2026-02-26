package com.kimiha.vortexcore.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CancelConfirm(
        String clOrderId,
        String origClOrderId,
        String market,
        String securityId,
        String side,
        String shareholderId,
        Integer qty,
        Double price,
        Integer cumQty,
        Integer canceledQty
) {
    public static final ReportType REPORT_TYPE = ReportType.CANCEL_CONFIRM;
}
