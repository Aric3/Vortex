package com.kimiha.vortexcore.engine;

import com.kimiha.vortexcore.model.domain.Order;

import java.util.List;

/** 撮合结果：成交明细 + 状态/累计量发生变化的订单（用于持久化 ORDER_UPDATED） */
public record MatchResult(List<TradeResult> tradeResults, List<Order> ordersUpdated) {
}
