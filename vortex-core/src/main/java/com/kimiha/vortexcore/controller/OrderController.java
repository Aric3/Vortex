package com.kimiha.vortexcore.controller;

import com.kimiha.vortexcore.model.OrderEntity;
import com.kimiha.vortexcore.service.TradingService;

import java.util.List;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/vclient")
public class OrderController {
    private final TradingService tradingService;

    public OrderController(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    @PostMapping("/orders")
    public OrderEntity createOrder(@RequestBody OrderEntity order) {
        System.out.println("Handling request in: " + Thread.currentThread());
        return tradingService.saveOrder(order);
    }

    @GetMapping("/orders")
    public List<OrderEntity> getAllOrders() {
        return tradingService.findAll();
    }

    @GetMapping("/orders/{clOrderId}")
    public OrderEntity getOrderByClOrderId(@PathVariable String clOrderId) {
        return tradingService.findByClOrderId(clOrderId);
    }
}
