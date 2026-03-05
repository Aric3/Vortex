package com.kimiha.vortexcore.model.dto.report;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 订单成交回报（异步推送），对应 API 规范 2.3。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderExecution(
        String clOrderId,
        String market,
        String securityId,
        String side,
        Integer qty,
        Double price,
        String shareholderId,
        String execId,
        Integer execQty,
        Double execPrice
) {
    public static final ReportType REPORT_TYPE = ReportType.ORDER_EXECUTION;
}
