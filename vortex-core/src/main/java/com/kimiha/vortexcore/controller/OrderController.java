package com.kimiha.vortexcore.controller;

import com.kimiha.vortexcore.model.OrderEntity;
import com.kimiha.vortexcore.model.CancellationEntity;
import com.kimiha.vortexcore.model.ProcessOrderResult;
import com.kimiha.vortexcore.model.Result;
import com.kimiha.vortexcore.model.ResultCode;
import com.kimiha.vortexcore.model.ValidationResult;
import com.kimiha.vortexcore.model.dto.OrderReportEnvelope;
import com.kimiha.vortexcore.service.OrderReportStreamService;
import com.kimiha.vortexcore.service.OrderService;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/vclient")
public class OrderController {
    private final OrderService orderService;
    private final OrderReportStreamService orderReportStreamService;

    public OrderController(OrderService orderService, OrderReportStreamService orderReportStreamService) {
        this.orderService = orderService;
        this.orderReportStreamService = orderReportStreamService;
    }

    @PostMapping("/orders")
    public Result createOrder(@RequestBody OrderEntity order) {
        ProcessOrderResult result = orderService.processOrder(order);
        if (!result.isSuccess()) {
            return Result.fail(ResultCode.VALIDATION_ERROR, "Order validation failed: " + result.getErrorMessage());
        }
        return Result.success(result.getSubmitResult(), "Order submitted; confirm/reject will be sent via reports.");
    }

    @GetMapping("/orders")
    public Result getAllOrders() {
        List<OrderEntity> all = orderService.findAll();
        return Result.success(all, "Orders retrieved successfully");
    }

    @PostMapping("/orders/cancel")
    public Result cancelOrder(@RequestBody CancellationEntity cancellation) {
        ValidationResult validation = orderService.cancelOrder(cancellation);
        if (!validation.isSuccess()) {
            return Result.fail(ResultCode.VALIDATION_ERROR, validation.getMessage());
        }
        return Result.success(null, "Cancel request submitted; confirm/reject will be sent via reports.");
    }

    @GetMapping("/orders/{clOrderId}")
    public Result getOrderByClOrderId(@PathVariable String clOrderId) {
        OrderEntity found = orderService.findByClOrderId(clOrderId);
        if (found == null) {
            return Result.fail(ResultCode.NOT_FOUND, "Order not found");
        }
        return Result.success(found, "Order retrieved successfully");
    }

    /**
     * 订单回报 SSE 流：客户端按股东号订阅，接收该股东下的订单成交回报、撤单确认回报等。
     * 连接后保持长连接，有回报时服务端推送。
     * GET /api/v1/vclient/stream/reports?shareholderId=xxx
     */
    @GetMapping(value = "/stream/reports", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<OrderReportEnvelope> streamReports(@RequestParam String shareholderId) {
        return orderReportStreamService.stream(shareholderId);
    }
}
