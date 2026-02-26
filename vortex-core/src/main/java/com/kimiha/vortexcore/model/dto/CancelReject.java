package com.kimiha.vortexcore.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CancelReject(
        String clOrderId,
        String origClOrderId,
        Integer rejectCode,
        String rejectText
) {
    public static final ReportType REPORT_TYPE = ReportType.CANCEL_REJECT;
}
