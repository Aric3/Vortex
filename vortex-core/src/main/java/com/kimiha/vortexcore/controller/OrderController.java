package com.kimiha.vortexcore.controller;

import com.kimiha.vortexcore.model.ProcessOrderResult;
import com.kimiha.vortexcore.model.Result;
import com.kimiha.vortexcore.model.ResultCode;
import com.kimiha.vortexcore.model.ValidationResult;
import com.kimiha.vortexcore.model.dto.OrderHistoryPageResponse;
import com.kimiha.vortexcore.model.dto.OrderResponse;
import com.kimiha.vortexcore.model.dto.OrderSubmitRequest;
import com.kimiha.vortexcore.model.dto.report.CancelRequest;
import com.kimiha.vortexcore.model.dto.report.OrderReportEnvelope;
import com.kimiha.vortexcore.service.OrderReportStreamService;
import com.kimiha.vortexcore.service.OrderService;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/vclient")
public class OrderController {

    private static final Logger log = LogManager.getLogger(OrderController.class);

    private final OrderService orderService;
    private final OrderReportStreamService orderReportStreamService;

    public OrderController(OrderService orderService, OrderReportStreamService orderReportStreamService) {
        this.orderService = orderService;
        this.orderReportStreamService = orderReportStreamService;
    }

    @PostMapping("/orders")
    public Result createOrder(@RequestBody OrderSubmitRequest request) {
        ProcessOrderResult result = orderService.processOrder(request);
        if (!result.isSuccess()) {
            return Result.fail(ResultCode.VALIDATION_ERROR, "Order validation failed: " + result.getErrorMessage());
        }
        return Result.success(result.getSubmitResult(), "Order submitted; confirm/reject will be sent via reports.");
    }

    @GetMapping("/orders")
    public Result getAllOrders() {
        List<OrderResponse> all = orderService.findAll().stream()
                .map(OrderResponse::fromEntity)
                .collect(Collectors.toList());
        return Result.success(all, "Orders retrieved successfully");
    }

    @GetMapping("/orders/history")
    public Result getOrderHistoryByShareholderId(
            @RequestParam String shareholderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (shareholderId == null || shareholderId.length() != 10) {
            return Result.fail(ResultCode.VALIDATION_ERROR, "shareholderId invalid");
        }
        if (page < 0) {
            return Result.fail(ResultCode.VALIDATION_ERROR, "page invalid");
        }
        if (size <= 0 || size > 200) {
            return Result.fail(ResultCode.VALIDATION_ERROR, "size invalid");
        }

        Page<OrderResponse> paged = orderService.findByShareholderId(shareholderId, page, size)
                .map(OrderResponse::fromEntity);
        OrderHistoryPageResponse response = new OrderHistoryPageResponse(
                paged.getContent(),
                paged.getNumber(),
                paged.getSize(),
                paged.getTotalElements(),
                paged.getTotalPages(),
                paged.isLast()
        );
        return Result.success(response, "Order history retrieved successfully");
    }

    @PostMapping("/orders/cancel")
    public Result cancelOrder(@RequestBody CancelRequest cancelRequest) {
        ValidationResult validation = orderService.cancelOrder(cancelRequest);
        if (!validation.isSuccess()) {
            return Result.fail(ResultCode.VALIDATION_ERROR, validation.getMessage());
        }
        return Result.success(null, "Cancel request submitted; confirm/reject will be sent via reports.");
    }

    @GetMapping("/orders/{clOrderId}")
    public Result getOrderByClOrderId(@PathVariable String clOrderId) {
        var found = orderService.findByClOrderId(clOrderId);
        if (found == null) {
            return Result.fail(ResultCode.NOT_FOUND, "Order not found");
        }
        return Result.success(OrderResponse.fromEntity(found), "Order retrieved successfully");
    }

    /**
     * 按股东号查询该股东作为 taker 的成交明细，按 clOrderId 分组。前端刷新后拉取以恢复多笔成交展示。
     * GET /api/v1/vclient/orders/executions?shareholderId=xxx
     */
    @GetMapping("/orders/executions")
    public Result getExecutionsByShareholderId(@RequestParam String shareholderId) {
        if (shareholderId == null || shareholderId.length() != 10) {
            return Result.fail(ResultCode.VALIDATION_ERROR, "shareholderId invalid");
        }
        return Result.success(orderService.findExecutionsByShareholderId(shareholderId), "Executions retrieved");
    }

    /**
     * 订单回报 SSE 流：客户端按股东号订阅，接收该股东下的订单成交回报、撤单确认回报等。
     * 连接后保持长连接，有回报时服务端推送
     * GET /api/v1/vclient/stream/reports?shareholderId=xxx
     */
    @GetMapping(value = "/stream/reports", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<OrderReportEnvelope> streamReports(@RequestParam String shareholderId) {
        log.info("[REPORT_STREAM] GET /stream/reports shareholderId={}", shareholderId);
        return orderReportStreamService.stream(shareholderId);
    }
}
