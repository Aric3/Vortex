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
public class CancelConfirmDto {
    public static final ReportType REPORT_TYPE = ReportType.CANCEL_CONFIRM;

    private String clOrderId; // 撤单请求的唯一编号 (16字节字符串)
    private String origClOrderId; // 已撤原始订单的唯一编号 (16字节字符串)
    private String market; // 已撤订单交易的市场 (4字节字符串) XSHG: 上交所, XSHE: 深交所, BJSE: 北交所
    private String securityId; // 已撤订单交易的股票代码 (6字节字符串)
    private String side; // 已撤订单买卖方向 (1字节字符串) B: 买, S: 卖
    private String shareholderId; // 已撤订单的股东号 (10字节字符串)
    private Integer qty; // 原始订单数量 (4字节无符号整数)
    private Double price; // 原始订单价格 (8字节浮点数)
    private Integer cumQty; // 累计成交数量 (4字节无符号整数)
    private Integer canceledQty; // 本次撤单成功的数量 (4字节无符号整数)
}
