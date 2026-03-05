package com.kimiha.vortexcore.matching;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MatchingEngine {
    // securityId -> OrderBook
    // 使用 ConcurrentHashMap 保证多线程安全
    private final Map<String, OrderBook> books = new ConcurrentHashMap<>();

    public OrderBook getOrderBook(String securityId) {
        return books.computeIfAbsent(securityId, OrderBook::new);
    }
}