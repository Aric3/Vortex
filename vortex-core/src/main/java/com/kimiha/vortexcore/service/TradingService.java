package com.kimiha.vortexcore.service;

import com.kimiha.vortexcore.model.OrderEntity;
import com.kimiha.vortexcore.repository.OrderRepository;

import java.util.List;

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
        // TODO: 基本的订单校验逻辑 实际不应该直接save到数据库
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