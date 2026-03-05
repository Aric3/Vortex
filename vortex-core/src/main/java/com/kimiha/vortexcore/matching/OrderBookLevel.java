package com.kimiha.vortexcore.matching;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 订单簿某一价格档位：价格、该价挂单总量、订单笔数 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderBookLevel {
    private Double price;
    private long totalQty;
    private int orderCount;
}
