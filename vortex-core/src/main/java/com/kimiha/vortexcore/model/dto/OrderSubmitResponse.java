package com.kimiha.vortexcore.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * POST /orders 成功时的“已提交”响应；确认/拒绝通过 stream/reports 推送。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderSubmitResponse(String clOrderId) {}
