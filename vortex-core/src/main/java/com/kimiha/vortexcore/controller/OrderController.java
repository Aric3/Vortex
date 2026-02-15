package com.kimiha.vortexcore.controller;

import com.kimiha.vortexcore.model.OrderEntity;
import com.kimiha.vortexcore.model.Result;
import com.kimiha.vortexcore.model.ResultCode;
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
    public Result createOrder(@RequestBody OrderEntity order) {
        System.out.println("Handling request in: " + Thread.currentThread());
        try {
            OrderEntity saved = tradingService.saveOrder(order);
            return Result.ok(saved, "accepted");
        } catch (IllegalArgumentException e) {
            return Result.fail(ResultCode.VALIDATION_ERROR, e.getMessage());
        } catch (Exception e) {
            return Result.fail(ResultCode.SYSTEM_ERROR, "system error");
        }
    }

    @GetMapping("/orders")
    public Result getAllOrders() {
        List<OrderEntity> all = tradingService.findAll();
        return Result.ok(all, "success");
    }

    @GetMapping("/orders/{clOrderId}")
    public Result getOrderByClOrderId(@PathVariable String clOrderId) {
        OrderEntity found = tradingService.findByClOrderId(clOrderId);
        if (found == null) {
            return Result.fail(ResultCode.NOT_FOUND, "order not found");
        }
        return Result.ok(found, "success");
    }
}
