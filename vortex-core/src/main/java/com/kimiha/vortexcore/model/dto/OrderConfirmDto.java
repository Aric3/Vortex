package com.kimiha.vortexcore.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderConfirmDto {
    public static final ReportType REPORT_TYPE = ReportType.ORDER_CONFIRM;

    private String clOrderId; // 订单的唯一编号 (16字节字符串)
    private String market; // 订单交易的市场 (4字节字符串) XSHG: 上交所, XSHE: 深交所, BJSE: 北交所
    private String securityId; // 订单交易的股票代码 (6字节字符串)
    private String side; // 订单买卖方向 (1字节字符串) B: 买, S: 卖
    private Integer qty; // 订单数量 (4字节无符号整数)
    private Double price; // 订单价格 (8字节浮点数)
    private String shareholderId; // 股东号 (10字节字符串)
}
