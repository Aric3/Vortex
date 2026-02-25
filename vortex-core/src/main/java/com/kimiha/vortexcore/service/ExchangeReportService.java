package com.kimiha.vortexcore.service;

import com.kimiha.vortexcore.model.ExchangeCancellationAcceptAck;
import com.kimiha.vortexcore.model.ExchangeCancellationRejectAck;
import com.kimiha.vortexcore.model.ExchangeOrderAcceptAck;
import com.kimiha.vortexcore.model.ExchangeOrderDealAck;
import com.kimiha.vortexcore.model.ExchangeOrderRejectAck;
import com.kimiha.vortexcore.model.OrderEventEntity;
import com.kimiha.vortexcore.model.OrderEventType;
import com.kimiha.vortexcore.model.TradeFillEntity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ExchangeReportService {
    private static final Logger log = LoggerFactory.getLogger(ExchangeReportService.class);
    private final AsyncPersistenceService asyncPersistenceService;

    public ExchangeReportService(AsyncPersistenceService asyncPersistenceService) {
        this.asyncPersistenceService = asyncPersistenceService;
    }

    @Async
    public void handleOrderAccept(ExchangeOrderAcceptAck ack) {
        log.info("Received exchange order accept: {}", ack);
        OrderEventEntity event = baseOrderEvent(ack.getClOrderId(), ack.getMarket(), ack.getSecurityId(),
                ack.getSide(), ack.getQty(), ack.getPrice(), ack.getShareholderId());
        event.setEventType(OrderEventType.ORDER_ACCEPT);
        asyncPersistenceService.saveOrderEvent(event);
    }

    @Async
    public void handleOrderReject(ExchangeOrderRejectAck ack) {
        log.info("Received exchange order reject: {}", ack);
        OrderEventEntity event = baseOrderEvent(ack.getClOrderId(), ack.getMarket(), ack.getSecurityId(),
                ack.getSide(), ack.getQty(), ack.getPrice(), ack.getShareholderId());
        event.setEventType(OrderEventType.ORDER_REJECT);
        event.setRejectCode(ack.getRejectCode());
        event.setRejectText(ack.getRejectText());
        asyncPersistenceService.saveOrderEvent(event);
    }

    @Async
    public void handleOrderDeal(ExchangeOrderDealAck ack) {
        log.info("Received exchange order deal: {}", ack);
        TradeFillEntity fill = new TradeFillEntity();
        fill.setClOrderId(ack.getClOrderId());
        fill.setMarket(ack.getMarket());
        fill.setSecurityId(ack.getSecurityId());
        fill.setSide(ack.getSide());
        fill.setQty(ack.getQty());
        fill.setPrice(ack.getPrice());
        fill.setShareholderId(ack.getShareholderId());
        fill.setExecId(ack.getExecId());
        fill.setExecQty(ack.getExecQty());
        fill.setExecPrice(ack.getExecPrice());
        asyncPersistenceService.saveTradeFill(fill);
    }

    @Async
    public void handleCancellationAccept(ExchangeCancellationAcceptAck ack) {
        log.info("Received exchange cancellation accept: {}", ack);
        OrderEventEntity event = baseOrderEvent(ack.getClOrderId(), ack.getMarket(), ack.getSecurityId(),
                ack.getSide(), ack.getQty(), ack.getPrice(), ack.getShareholderId());
        event.setEventType(OrderEventType.CANCEL_ACCEPT);
        event.setOrigClOrderId(ack.getOrigClOrderId());
        event.setCumQty(ack.getCumQty());
        event.setCanceledQty(ack.getCanceledQty());
        asyncPersistenceService.saveOrderEvent(event);
    }

    @Async
    public void handleCancellationReject(ExchangeCancellationRejectAck ack) {
        log.info("Received exchange cancellation reject: {}", ack);
        OrderEventEntity event = new OrderEventEntity();
        event.setEventType(OrderEventType.CANCEL_REJECT);
        event.setClOrderId(ack.getClOrderId());
        event.setOrigClOrderId(ack.getOrigClOrderId());
        event.setRejectCode(ack.getRejectCode());
        event.setRejectText(ack.getRejectText());
        asyncPersistenceService.saveOrderEvent(event);
    }

    private OrderEventEntity baseOrderEvent(
            String clOrderId,
            String market,
            String securityId,
            String side,
            Integer qty,
            Double price,
            String shareholderId) {
        OrderEventEntity event = new OrderEventEntity();
        event.setClOrderId(clOrderId);
        event.setMarket(market);
        event.setSecurityId(securityId);
        event.setSide(side);
        event.setQty(qty);
        event.setPrice(price);
        event.setShareholderId(shareholderId);
        return event;
    }
}
