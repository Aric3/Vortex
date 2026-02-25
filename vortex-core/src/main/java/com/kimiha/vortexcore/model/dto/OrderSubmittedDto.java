package com.kimiha.vortexcore.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POST /orders 成功时的“已提交”响应，仅表示请求已入队；确认/拒绝通过 stream/reports 推送。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderSubmittedDto {
    private String clOrderId;
}
