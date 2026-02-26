package com.kimiha.vortexcore.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 订单非法回报（api_spec 2.2），如对敲检测不通过时推送。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderReject(
        String clOrderId,
        String market,
        String securityId,
        String side,
        Integer qty,
        Double price,
        String shareholderId,
        Integer rejectCode,
        String rejectText
) {
    public static final ReportType REPORT_TYPE = ReportType.ORDER_REJECT;
}
