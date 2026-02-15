package com.kimiha.vortexcore.service;

import com.kimiha.vortexcore.model.OrderEntity;
import com.kimiha.vortexcore.repository.OrderRepository;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradingService {



private final OrderRepository orderRepository;

    public TradingService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderEntity saveOrder(OrderEntity order) {
        validateOrder(order);
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public OrderEntity findByClOrderId(String clOrderId) {
        return orderRepository.findByClOrderId(clOrderId);
    }

    @Transactional(readOnly = true)
    public List<OrderEntity> findAll() {
        return orderRepository.findAll();
    }

    private void validateOrder(OrderEntity order) {
        if (order == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (order.getClOrderId() == null || order.getClOrderId().isEmpty() || order.getClOrderId().length() > 16) {
            throw new IllegalArgumentException("clOrderId 非法");
        }
        if (order.getMarket() == null || order.getMarket().length() != 4 || !Set.of("XSHG", "XSHE", "BJSE").contains(order.getMarket())) {
            throw new IllegalArgumentException("market 非法");
        }
        if (order.getSecurityId() == null || !order.getSecurityId().matches("\\d{6}")) {
            throw new IllegalArgumentException("securityId 非法");
        }
        if (order.getSide() == null || order.getSide().length() != 1 || !(order.getSide().equals("B") || order.getSide().equals("S"))) {
            throw new IllegalArgumentException("side 非法");
        }
        if (order.getQty() < 0) {
            throw new IllegalArgumentException("qty 非法");
        }
        if (order.getPrice() == null || order.getPrice() <= 0) {
            throw new IllegalArgumentException("price 非法");
        }
        if (order.getShareholderId() == null || order.getShareholderId().isEmpty() || order.getShareholderId().length() > 10) {
            throw new IllegalArgumentException("shareholderId 非法");
        }
    }
}
