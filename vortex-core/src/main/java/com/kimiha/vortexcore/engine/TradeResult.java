package com.kimiha.vortexcore.engine;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TradeResult {
    private String takerClOrderId;  // 进攻方单号
    private String makerClOrderId;  // 挂单方单号
    private Double price;           // 成交价
    private int qty;               // 成交量
    private String securityId;     // 股票代码
}