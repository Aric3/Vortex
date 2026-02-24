package com.kimiha.vortexcore.service;

import com.kimiha.vortexcore.model.OrderEntity;
import com.kimiha.vortexcore.repository.OrderRepository;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradingService {
    private final OrderRepository orderRepository;
    private final OrderValidator orderValidator;

    public TradingService(OrderRepository orderRepository, OrderValidator orderValidator) {
        this.orderRepository = orderRepository;
        this.orderValidator = orderValidator;
    }

    @Transactional
    public OrderEntity saveOrder(OrderEntity order) {
        orderValidator.validate(order);
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
}
