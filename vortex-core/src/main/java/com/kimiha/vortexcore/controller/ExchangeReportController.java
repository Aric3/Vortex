package com.kimiha.vortexcore.controller;

import com.kimiha.vortexcore.model.ExchangeCancellationAcceptAck;
import com.kimiha.vortexcore.model.ExchangeCancellationRejectAck;
import com.kimiha.vortexcore.model.ExchangeOrderAcceptAck;
import com.kimiha.vortexcore.model.ExchangeOrderDealAck;
import com.kimiha.vortexcore.model.ExchangeOrderRejectAck;
import com.kimiha.vortexcore.model.Result;
import com.kimiha.vortexcore.model.ResultCode;
import com.kimiha.vortexcore.service.ExchangeReportService;
import com.kimiha.vortexcore.service.ExchangeReportValidator;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vexchange/ack")
public class ExchangeReportController {
    private final ExchangeReportService exchangeReportService;
    private final ExchangeReportValidator exchangeReportValidator;

    public ExchangeReportController(
            ExchangeReportService exchangeReportService,
            ExchangeReportValidator exchangeReportValidator) {
        this.exchangeReportService = exchangeReportService;
        this.exchangeReportValidator = exchangeReportValidator;
    }

    @PostMapping("/order-accept")
    public Result receiveOrderAccept(@RequestBody ExchangeOrderAcceptAck ack) {
        try {
            exchangeReportValidator.validateOrderAccept(ack);
            exchangeReportService.handleOrderAccept(ack);
            return Result.success(java.util.Map.of(), "Order accept received.");
        } catch (IllegalArgumentException e) {
            return Result.fail(ResultCode.VALIDATION_ERROR, "Order accept validation failed: " + e.getMessage());
        } catch (Exception e) {
            return Result.fail(ResultCode.SYSTEM_ERROR, "System error");
        }
    }

    @PostMapping("/order-reject")
    public Result receiveOrderReject(@RequestBody ExchangeOrderRejectAck ack) {
        try {
            exchangeReportValidator.validateOrderReject(ack);
            exchangeReportService.handleOrderReject(ack);
            return Result.success(java.util.Map.of(), "Order reject received.");
        } catch (IllegalArgumentException e) {
            return Result.fail(ResultCode.VALIDATION_ERROR, "Order reject validation failed: " + e.getMessage());
        } catch (Exception e) {
            return Result.fail(ResultCode.SYSTEM_ERROR, "System error");
        }
    }

    @PostMapping("/order-deal")
    public Result receiveOrderDeal(@RequestBody ExchangeOrderDealAck ack) {
        try {
            exchangeReportValidator.validateOrderDeal(ack);
            exchangeReportService.handleOrderDeal(ack);
            return Result.success(java.util.Map.of(), "Order deal received.");
        } catch (IllegalArgumentException e) {
            return Result.fail(ResultCode.VALIDATION_ERROR, "Order deal validation failed: " + e.getMessage());
        } catch (Exception e) {
            return Result.fail(ResultCode.SYSTEM_ERROR, "System error");
        }
    }

    @PostMapping("/cancellation-accept")
    public Result receiveCancellationAccept(@RequestBody ExchangeCancellationAcceptAck ack) {
        try {
            exchangeReportValidator.validateCancellationAccept(ack);
            exchangeReportService.handleCancellationAccept(ack);
            return Result.success(java.util.Map.of(), "Cancellation accept received.");
        } catch (IllegalArgumentException e) {
            return Result.fail(ResultCode.VALIDATION_ERROR, "Cancellation accept validation failed: " + e.getMessage());
        } catch (Exception e) {
            return Result.fail(ResultCode.SYSTEM_ERROR, "System error");
        }
    }

    @PostMapping("/cancellation-reject")
    public Result receiveCancellationReject(@RequestBody ExchangeCancellationRejectAck ack) {
        try {
            exchangeReportValidator.validateCancellationReject(ack);
            exchangeReportService.handleCancellationReject(ack);
            return Result.success(java.util.Map.of(), "Cancellation reject received.");
        } catch (IllegalArgumentException e) {
            return Result.fail(ResultCode.VALIDATION_ERROR, "Cancellation reject validation failed: " + e.getMessage());
        } catch (Exception e) {
            return Result.fail(ResultCode.SYSTEM_ERROR, "System error");
        }
    }
}
