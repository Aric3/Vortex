package com.kimiha.vortexcore.engine;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 订单簿快照：买盘/卖盘各若干档 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderBookSnapshot {
    private String securityId;
    private List<OrderBookLevel> bids;  // 买盘，从高到低
    private List<OrderBookLevel> asks;  // 卖盘，从低到高
    private long timestamp;
}
