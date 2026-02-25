package com.kimiha.vortexcore.service;

import com.kimiha.vortexcore.model.OrderEntity;

import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class OrderValidator {
    private static final Set<String> VALID_MARKETS = Set.of("XSHG", "XSHE", "BJSE");

    public void validate(OrderEntity order) {
        if (order == null) {
            throw new IllegalArgumentException("order is null");
        }
        if (order.getClOrderId() == null || order.getClOrderId().length() != 16) {
            throw new IllegalArgumentException("clOrderId invalid");
        }
        if (order.getMarket() == null || order.getMarket().length() != 4 || !VALID_MARKETS.contains(order.getMarket())) {
            throw new IllegalArgumentException("market invalid");
        }
        if (order.getSecurityId() == null || !order.getSecurityId().matches("\\d{6}")) {
            throw new IllegalArgumentException("securityId invalid");
        }
        if (order.getSide() == null || order.getSide().length() != 1 || !(order.getSide().equals("B") || order.getSide().equals("S"))) {
            throw new IllegalArgumentException("side invalid");
        }
        if (order.getQty() < 0) {
            throw new IllegalArgumentException("qty invalid");
        }
        if (order.getPrice() == null || order.getPrice() <= 0) {
            throw new IllegalArgumentException("price invalid");
        }
        if (order.getShareholderId() == null || order.getShareholderId().length() != 10) {
            throw new IllegalArgumentException("shareholderId invalid");
        }
    }
}
