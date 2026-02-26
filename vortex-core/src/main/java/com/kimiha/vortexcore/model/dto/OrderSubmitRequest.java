package com.kimiha.vortexcore.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * POST /orders 请求体：下单参数
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderSubmitRequest(
    String clOrderId, // 客户端订单号 (16字节字符串)
    String market, // 市场 (4字节字符串) XSHG: 上交所, XSHE: 深交所, BJSE: 北交所   
    String securityId, // 股票代码 (6字节字符串)
    String side, // 买卖方向 (1字节字符串) B: 买, S: 卖
    Integer qty, // 订单数量 (4字节无符号整数)
    Double price, // 订单价格 (8字节浮点数)
    String shareholderId // 股东号 (10字节字符串)
) {}
