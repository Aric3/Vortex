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

    private String makerSide; // 挂单方买卖方向 (1字节字符串) B: 买, S: 卖
    private String makerShareholderId; // 挂单方股东号
    private int makerOriginalQty; // 挂单方原始数量 
    private Double makerPrice; // 挂单方价格
}