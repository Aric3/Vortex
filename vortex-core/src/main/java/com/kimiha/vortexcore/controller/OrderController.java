package com.kimiha.vortexcore.controller;

import com.kimiha.vortexcore.model.OrderEntity;
import com.kimiha.vortexcore.model.ProcessOrderResult;
import com.kimiha.vortexcore.model.Result;
import com.kimiha.vortexcore.model.ResultCode;
import com.kimiha.vortexcore.service.OrderService;

import java.util.List;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/vclient")
public class OrderController {
    private final OrderService tradingService;

    public OrderController(OrderService tradingService) {
        this.tradingService = tradingService;
    }

    @PostMapping("/orders")
    public Result createOrder(@RequestBody OrderEntity order) {
        ProcessOrderResult outcome = tradingService.processOrder(order);
        if (!outcome.isSuccess()) {
            return Result.fail(ResultCode.VALIDATION_ERROR, "Order validation failed: " + outcome.getErrorMessage());
        }
        return Result.success(outcome.getOrder(), "The order is valid and has been forwarded to the exchange.");
    }

    @GetMapping("/orders")
    public Result getAllOrders() {
        List<OrderEntity> all = tradingService.findAll();
        return Result.success(all, "Orders retrieved successfully");
    }

    @GetMapping("/orders/{clOrderId}")
    public Result getOrderByClOrderId(@PathVariable String clOrderId) {
        OrderEntity found = tradingService.findByClOrderId(clOrderId);
        if (found == null) {
            return Result.fail(ResultCode.NOT_FOUND, "Order not found");
        }
        return Result.success(found, "Order retrieved successfully");
    }
}
