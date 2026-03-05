package com.kimiha.vortexcore.model.dto.report;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * POST /orders/cancel 请求体：撤单参数
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CancelRequest(
    String clOrderId,
    String origClOrderId,
    String market,
    String securityId,
    String side,
    String shareholderId
) {}
