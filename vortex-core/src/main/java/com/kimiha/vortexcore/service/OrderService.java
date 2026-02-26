package com.kimiha.vortexcore.service;

import com.kimiha.vortexcore.disruptor.OrderEvent;
import com.kimiha.vortexcore.model.CancellationEntity;
import com.kimiha.vortexcore.model.OrderEntity;
import com.kimiha.vortexcore.model.ProcessOrderResult;
import com.kimiha.vortexcore.model.ValidationResult;
import com.kimiha.vortexcore.model.dto.OrderSubmittedDto;
import com.kimiha.vortexcore.repository.OrderRepository;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private static final Set<String> VALID_MARKETS = Set.of("XSHG", "XSHE", "BJSE");

    private final OrderRepository orderRepository;
    private final Disruptor<OrderEvent> disruptor;

    public OrderService(OrderRepository orderRepository, Disruptor<OrderEvent> disruptor) {
        this.orderRepository = orderRepository;
        this.disruptor = disruptor;
    }

    @Transactional
    public ProcessOrderResult processOrder(OrderEntity order) {
        ValidationResult validation = validateOrder(order);
        if (!validation.isSuccess()) {
            return new ProcessOrderResult(validation, null);
        }
        RingBuffer<OrderEvent> ringBuffer = disruptor.getRingBuffer();
        ringBuffer.publishEvent((event, sequence) -> event.setOrder(order));
        return new ProcessOrderResult(ValidationResult.pass(),
                OrderSubmittedDto.builder().clOrderId(order.getClOrderId()).build());
    }

    public ValidationResult validateOrder(OrderEntity order) {
        if (order == null) {
            return ValidationResult.fail("order is null");
        }
        if (order.getClOrderId() == null || order.getClOrderId().length() != 16) {
            return ValidationResult.fail("clOrderId invalid");
        }
        if (order.getMarket() == null || order.getMarket().length() != 4 || !VALID_MARKETS.contains(order.getMarket())) {
            return ValidationResult.fail("market invalid");
        }
        if (order.getSecurityId() == null || !order.getSecurityId().matches("\\d{6}")) {
            return ValidationResult.fail("securityId invalid");
        }
        if (order.getSide() == null || order.getSide().length() != 1 || !(order.getSide().equals("B") || order.getSide().equals("S"))) {
            return ValidationResult.fail("side invalid");
        }
        if (order.getQty() < 0) {
            return ValidationResult.fail("qty invalid");
        }
        if (order.getPrice() == null || order.getPrice() <= 0) {
            return ValidationResult.fail("price invalid");
        }
        if (order.getShareholderId() == null || order.getShareholderId().length() != 10) {
            return ValidationResult.fail("shareholderId invalid");
        }
        return ValidationResult.pass();
    }

    @Transactional(readOnly = true)
    public OrderEntity findByClOrderId(String clOrderId) {
        return orderRepository.findByClOrderId(clOrderId);
    }

    @Transactional(readOnly = true)
    public List<OrderEntity> findAll() {
        return orderRepository.findAll();
    }

    /**
     * 提交撤单请求：校验通过后发布撤单事件，异步处理；确认/拒绝通过 SSE 回报推送。
     *
     * @param cancelRequest 撤单请求（clOrderId=撤单请求编号，origClOrderId=待撤订单号，及 market/securityId/side/shareholderId）
     * @return 校验通过返回 pass()，否则返回 fail(message)
     */
    public ValidationResult cancelOrder(CancellationEntity cancellation) {
        ValidationResult validation = validateCancelRequest(cancellation);
        if (!validation.isSuccess()) {
            return validation;
        }
        RingBuffer<OrderEvent> ringBuffer = disruptor.getRingBuffer();
        ringBuffer.publishEvent((event, sequence) -> {
            event.setCancel(true);
            event.setCancellation(cancellation);
        });
        return ValidationResult.pass();
    }

    private ValidationResult validateCancelRequest(CancellationEntity cancellation) {
        if (cancellation == null) {
            return ValidationResult.fail("cancel request is null");
        }
        if (cancellation.getClOrderId() == null || cancellation.getClOrderId().length() != 16) {
            return ValidationResult.fail("clOrderId invalid");
        }
        if (cancellation.getOrigClOrderId() == null || cancellation.getOrigClOrderId().length() != 16) {
            return ValidationResult.fail("origClOrderId invalid");
        }
        if (cancellation.getMarket() == null || cancellation.getMarket().length() != 4 || !VALID_MARKETS.contains(cancellation.getMarket())) {
            return ValidationResult.fail("market invalid");
        }
        if (cancellation.getSecurityId() == null || !cancellation.getSecurityId().matches("\\d{6}")) {
            return ValidationResult.fail("securityId invalid");
        }
        if (cancellation.getSide() == null || cancellation.getSide().length() != 1 || !(cancellation.getSide().equals("B") || cancellation.getSide().equals("S"))) {
            return ValidationResult.fail("side invalid");
        }
        if (cancellation.getShareholderId() == null || cancellation.getShareholderId().length() != 10) {
            return ValidationResult.fail("shareholderId invalid");
        }
        return ValidationResult.pass();
    }
}
