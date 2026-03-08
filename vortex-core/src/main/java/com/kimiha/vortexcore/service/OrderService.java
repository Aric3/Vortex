package com.kimiha.vortexcore.service;

import com.kimiha.vortexcore.model.entity.CancellationEntity;
import com.kimiha.vortexcore.model.entity.OrderEntity;
import com.kimiha.vortexcore.disruptor.order.OrderEvent;
import com.kimiha.vortexcore.model.ProcessOrderResult;
import com.kimiha.vortexcore.model.ValidationResult;
import com.kimiha.vortexcore.model.domain.Order;
import com.kimiha.vortexcore.model.dto.OrderSubmitRequest;
import com.kimiha.vortexcore.model.dto.OrderSubmitResponse;
import com.kimiha.vortexcore.model.dto.ExecutionDto;
import com.kimiha.vortexcore.model.dto.report.CancelRequest;
import com.kimiha.vortexcore.model.entity.TradeEntity;
import com.kimiha.vortexcore.repository.OrderRepository;
import com.kimiha.vortexcore.repository.TradeRepository;
import com.lmax.disruptor.RingBuffer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import com.lmax.disruptor.dsl.Disruptor;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger log = LogManager.getLogger(OrderService.class);
    private static final Set<String> VALID_MARKETS = Set.of("XSHG", "XSHE", "BJSE");

    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final Disruptor<OrderEvent> disruptor;

    public OrderService(OrderRepository orderRepository, TradeRepository tradeRepository, Disruptor<OrderEvent> disruptor) {
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
        this.disruptor = disruptor;
    }

    /**
     * 按股东号查询该股东作为 taker 的成交明细，按 clOrderId 分组，用于前端刷新后恢复多笔成交展示。
     */
    @Transactional(readOnly = true)
    public Map<String, List<ExecutionDto>> findExecutionsByShareholderId(String shareholderId) {
        if (shareholderId == null || shareholderId.length() != 10) {
            return Map.of();
        }
        List<TradeEntity> trades = tradeRepository.findByTakerShareholderIdOrderByTradeTimeEpochMsAsc(shareholderId);
        return trades.stream()
                .collect(Collectors.groupingBy(TradeEntity::getTakerClOrderId,
                        LinkedHashMap::new,
                        Collectors.mapping(t -> new ExecutionDto(t.getExecId(), t.getQty(), t.getPrice()),
                                Collectors.toCollection(ArrayList::new))));
    }

    @Transactional
    public ProcessOrderResult processOrder(OrderSubmitRequest request) {
        Order order = Order.fromRequest(request);
        ValidationResult validation = validateOrder(order);
        if (!validation.isSuccess()) {
            log.warn("[ORDER_SUBMIT] validation failed clOrderId={} shareholderId={} reason={}",
                    request.clOrderId(), request.shareholderId(), validation.getMessage());
            return new ProcessOrderResult(validation, null);
        }
        RingBuffer<OrderEvent> ringBuffer = disruptor.getRingBuffer();
        ringBuffer.publishEvent((event, sequence) -> event.setOrder(order));
        log.info("[ORDER_SUBMIT] published to disruptor clOrderId={} shareholderId={} securityId={}",
                order.getClOrderId(), order.getShareholderId(), order.getSecurityId());
        return new ProcessOrderResult(ValidationResult.pass(), new OrderSubmitResponse(order.getClOrderId()));
    }

    public ValidationResult validateOrder(Order order) {
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
        if (order.getQty() <= 0) {
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

    @Transactional(readOnly = true)
    public Page<OrderEntity> findByShareholderId(String shareholderId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTimeEpochMs"));
        return orderRepository.findByShareholderId(shareholderId, pageRequest);
    }

    /**
     * 提交撤单请求：校验通过后发布撤单事件，异步处理；确认/拒绝通过 SSE 回报推送。
     */
    public ValidationResult cancelOrder(CancelRequest cancelRequest) {
        ValidationResult validation = validateCancelRequest(cancelRequest);
        if (!validation.isSuccess()) {
            return validation;
        }
        CancellationEntity cancellation = toCancellationEntity(cancelRequest);
        RingBuffer<OrderEvent> ringBuffer = disruptor.getRingBuffer();
        ringBuffer.publishEvent((event, sequence) -> {
            event.setCancel(true);
            event.setCancellation(cancellation);
        });
        return ValidationResult.pass();
    }

    private static CancellationEntity toCancellationEntity(CancelRequest r) {
        CancellationEntity e = new CancellationEntity();
        e.setClOrderId(r.clOrderId());
        e.setOrigClOrderId(r.origClOrderId());
        e.setMarket(r.market());
        e.setSecurityId(r.securityId());
        e.setSide(r.side());
        e.setShareholderId(r.shareholderId());
        return e;
    }

    private ValidationResult validateCancelRequest(CancelRequest r) {
        if (r == null) {
            return ValidationResult.fail("cancel request is null");
        }
        if (r.clOrderId() == null || r.clOrderId().length() != 16) {
            return ValidationResult.fail("clOrderId invalid");
        }
        if (r.origClOrderId() == null || r.origClOrderId().length() != 16) {
            return ValidationResult.fail("origClOrderId invalid");
        }
        if (r.market() == null || r.market().length() != 4 || !VALID_MARKETS.contains(r.market())) {
            return ValidationResult.fail("market invalid");
        }
        if (r.securityId() == null || !r.securityId().matches("\\d{6}")) {
            return ValidationResult.fail("securityId invalid");
        }
        if (r.side() == null || r.side().length() != 1 || !(r.side().equals("B") || r.side().equals("S"))) {
            return ValidationResult.fail("side invalid");
        }
        if (r.shareholderId() == null || r.shareholderId().length() != 10) {
            return ValidationResult.fail("shareholderId invalid");
        }
        return ValidationResult.pass();
    }
}
