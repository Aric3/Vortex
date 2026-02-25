package com.kimiha.vortexcore.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订单成交回报（异步推送），对应 API 规范 2.3。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderExecutionDto {
    public static final ReportType REPORT_TYPE = ReportType.ORDER_EXECUTION;

    private String clOrderId; // 订单的唯一编号 (16字节字符串)
    private String market; // 订单交易的市场 (4字节字符串) XSHG: 上交所, XSHE: 深交所, BJSE: 北交所
    private String securityId; // 订单交易的股票代码 (6字节字符串)
    private String side; // 订单买卖方向 (1字节字符串) B: 买, S: 卖
    private Integer qty;      // 订单原始数量 (4字节无符号整数)
    private Double price;     // 订单原始价格
    private String shareholderId; // 股东号 (10字节字符串)
    private String execId;    // 成交唯一编号 (12 字节字符串)
    private Integer execQty;  // 本次成交数量
    private Double execPrice; // 本次成交价格
}
