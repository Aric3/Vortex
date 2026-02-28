package com.kimiha.vortexcore.disruptor;

import com.kimiha.vortexcore.engine.MatchResult;
import com.kimiha.vortexcore.engine.MatchingEngine;
import com.kimiha.vortexcore.engine.OrderBook;
import com.kimiha.vortexcore.engine.OrderBookChangedEvent;
import com.kimiha.vortexcore.engine.TradeResult;
import com.kimiha.vortexcore.model.entity.CancellationEntity;
import com.kimiha.vortexcore.model.OrderStatus;
import com.kimiha.vortexcore.model.domain.Order;
import com.kimiha.vortexcore.model.ResultCode;
import com.kimiha.vortexcore.model.dto.CancelConfirm;
import com.kimiha.vortexcore.model.dto.CancelReject;
import com.kimiha.vortexcore.model.dto.OrderConfirm;
import com.kimiha.vortexcore.model.dto.OrderExecution;
import com.kimiha.vortexcore.model.dto.OrderReject;
import com.kimiha.vortexcore.model.dto.OrderReportEnvelope;
import com.kimiha.vortexcore.service.OrderReportStreamService;
import com.kimiha.vortexcore.service.tools.ExecIdGenerator;
import com.lmax.disruptor.EventHandler;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class OrderEventHandler implements EventHandler<OrderEvent> {

    private static final String REJECT_TEXT_WASH_TRADE = "Wash trade rejected";
    private static final String REJECT_TEXT_DUPLICATE_CL_ORDER_ID = "Duplicate clOrderId";
    private static final String REJECT_TEXT_CANCEL_ORDER_NOT_FOUND = "Order not found or already filled/canceled";

    private final MatchingEngine matchingEngine;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderReportStreamService reportStreamService;
    private final PersistenceEventProducer persistenceEventProducer;

    public OrderEventHandler(MatchingEngine matchingEngine, ApplicationEventPublisher eventPublisher,
                             OrderReportStreamService reportStreamService,
                             @Autowired(required = false) PersistenceEventProducer persistenceEventProducer) {
        this.matchingEngine = matchingEngine;
        this.eventPublisher = eventPublisher;
        this.reportStreamService = reportStreamService;
        this.persistenceEventProducer = persistenceEventProducer;
    }

    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        if (event.isCancel()) {
            handleCancel(event);
            return;
        }
        Order order = event.getOrder();
        String securityId = order.getSecurityId();
        OrderBook book = matchingEngine.getOrderBook(securityId);

        if (book.illegalClOrderId(order)) {
            OrderReject reject = new OrderReject(
                    order.getClOrderId(), order.getMarket(), securityId, order.getSide(),
                    order.getQty(), order.getPrice(), order.getShareholderId(),
                    ResultCode.DUPLICATE_CL_ORDER_ID.getCode(), REJECT_TEXT_DUPLICATE_CL_ORDER_ID);
            if (reportStreamService != null) {
                reportStreamService.pushReport(order.getShareholderId(),
                        new OrderReportEnvelope(OrderReject.REPORT_TYPE, reject));
            }
            if (persistenceEventProducer != null) {
                persistenceEventProducer.publish(PersistenceEventType.ORDER_REJECT, reject);
            }
            return;
        }

        if (book.isWashTrading(order)) {
            OrderReject reject = new OrderReject(
                    order.getClOrderId(), order.getMarket(), securityId, order.getSide(),
                    order.getQty(), order.getPrice(), order.getShareholderId(),
                    ResultCode.WASH_TRADE_REJECT.getCode(), REJECT_TEXT_WASH_TRADE);
            if (reportStreamService != null) {
                reportStreamService.pushReport(order.getShareholderId(),
                        new OrderReportEnvelope(OrderReject.REPORT_TYPE, reject));
            }
            if (persistenceEventProducer != null) {
                persistenceEventProducer.publish(PersistenceEventType.ORDER_REJECT, reject);
            }
            return;
        }

        // 状态机：入簿前设定 orderQty、cumQty、status
        order.setOrderQty(order.getQty());
        order.setCumQty(0);
        order.setStatus(OrderStatus.New);
        order.setUpdatedTime(LocalDateTime.now());

        if (reportStreamService != null) {
            OrderConfirm confirm = new OrderConfirm(
                    order.getClOrderId(), order.getMarket(), securityId, order.getSide(),
                    order.getQty(), order.getPrice(), order.getShareholderId());
            reportStreamService.pushReport(order.getShareholderId(),
                    new OrderReportEnvelope(OrderConfirm.REPORT_TYPE, confirm));
        }
        if (persistenceEventProducer != null) {
            persistenceEventProducer.publish(PersistenceEventType.ORDER_ACCEPTED, Order.copySnapshot(order));
        }

        int takerOriginalQty = order.getQty();
        MatchResult matchResult = book.executeMatch(order);
        List<TradeResult> tradeResults = matchResult.tradeResults();

        if (!tradeResults.isEmpty()) {
            LocalDateTime tradeTime = LocalDateTime.now();
            for (TradeResult tr : tradeResults) {
                String execId = ExecIdGenerator.next();
                if (reportStreamService != null) {
                    // Taker 成交回报
                    OrderExecution takerReport = new OrderExecution(
                            tr.takerClOrderId(), order.getMarket(), securityId, order.getSide(),
                            takerOriginalQty, order.getPrice(), order.getShareholderId(),
                            execId, tr.qty(), tr.price());
                    reportStreamService.pushReport(order.getShareholderId(),
                            new OrderReportEnvelope(OrderExecution.REPORT_TYPE, takerReport));
                    // Maker 成交回报
                    OrderExecution makerReport = new OrderExecution(
                            tr.makerClOrderId(), order.getMarket(), securityId, tr.makerSide(),
                            tr.makerOriginalQty(), tr.makerPrice(), tr.makerShareholderId(),
                            execId, tr.qty(), tr.price());
                    reportStreamService.pushReport(tr.makerShareholderId(),
                            new OrderReportEnvelope(OrderExecution.REPORT_TYPE, makerReport));
                }
                if (persistenceEventProducer != null) {
                    TradePersistencePayload payload = new TradePersistencePayload(execId, tradeTime, order.getMarket(), tr,
                            order.getSide(), order.getShareholderId());
                    persistenceEventProducer.publish(PersistenceEventType.TRADE, payload);
                }
            }
            if (persistenceEventProducer != null) {
                for (Order o : matchResult.ordersUpdated()) {
                    persistenceEventProducer.publish(PersistenceEventType.ORDER_UPDATED, o);
                }
            }
        }

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new OrderBookChangedEvent(this, securityId));
        }
    }

    /**
     * 处理撤单请求
     * @param event 撤单请求事件
     */
    private void handleCancel(OrderEvent event) {
        CancellationEntity cancellation = event.getCancellation();
        String origClOrderId = cancellation.getOrigClOrderId();
        String securityId = cancellation.getSecurityId();
        if (persistenceEventProducer != null) {
            persistenceEventProducer.publish(PersistenceEventType.CANCEL_REQUEST, cancellation);
        }
        OrderBook book = matchingEngine.getOrderBook(securityId);
        // 从订单簿中移除原始订单
        Order removed = book.cancelByClOrderId(cancellation);

        if (reportStreamService != null) {
            if (removed != null) {
                CancelConfirm confirm = new CancelConfirm(
                        cancellation.getClOrderId(), origClOrderId, cancellation.getMarket(), securityId,
                        removed.getSide(), removed.getShareholderId(),
                        removed.getOrderQty() > 0 ? removed.getOrderQty() : (removed.getQty() + removed.getCumQty()),
                        removed.getPrice(), removed.getCumQty(), removed.getQty());
                reportStreamService.pushReport(cancellation.getShareholderId(),
                        new OrderReportEnvelope(CancelConfirm.REPORT_TYPE, confirm));
                if (persistenceEventProducer != null) {
                    CancelResultPayload payload = CancelResultPayload.builder()
                            .cancelRequestClOrderId(cancellation.getClOrderId())
                            .origClOrderId(origClOrderId)
                            .success(true)
                            .canceledQty(removed.getQty())
                            .cumQty(removed.getCumQty())
                            .build();
                    persistenceEventProducer.publish(PersistenceEventType.CANCEL_CONFIRMED, payload);
                }
            } else {
                CancelReject reject = new CancelReject(
                        cancellation.getClOrderId(), origClOrderId,
                        ResultCode.NOT_FOUND.getCode(), REJECT_TEXT_CANCEL_ORDER_NOT_FOUND);
                reportStreamService.pushReport(cancellation.getShareholderId(),
                        new OrderReportEnvelope(CancelReject.REPORT_TYPE, reject));
                if (persistenceEventProducer != null) {
                    CancelResultPayload payload = CancelResultPayload.builder()
                            .cancelRequestClOrderId(cancellation.getClOrderId())
                            .origClOrderId(origClOrderId)
                            .success(false)
                            .rejectCode(ResultCode.NOT_FOUND.getCode())
                            .rejectText(REJECT_TEXT_CANCEL_ORDER_NOT_FOUND)
                            .build();
                    persistenceEventProducer.publish(PersistenceEventType.CANCEL_REJECTED, payload);
                }
            }
        }

        if (removed != null && eventPublisher != null) {
            eventPublisher.publishEvent(new OrderBookChangedEvent(this, securityId));
        }
    }
}