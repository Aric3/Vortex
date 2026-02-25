package com.kimiha.vortexcore.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订单非法回报（api_spec 2.2），如对敲检测不通过时推送。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderRejectDto {
    public static final ReportType REPORT_TYPE = ReportType.ORDER_REJECT;

    private String clOrderId;
    private String market;
    private String securityId;
    private String side;
    private Integer qty;
    private Double price;
    private String shareholderId;
    private Integer rejectCode;
    private String rejectText;
}
