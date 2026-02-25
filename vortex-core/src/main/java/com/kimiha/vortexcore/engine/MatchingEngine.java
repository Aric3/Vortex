package com.kimiha.vortexcore.engine;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class MatchingEngine {
    // securityId -> OrderBook
    private final Map<String, OrderBook> books = new HashMap<>();

    public OrderBook getOrderBook(String securityId) {
        return books.computeIfAbsent(securityId, OrderBook::new);
    }
}