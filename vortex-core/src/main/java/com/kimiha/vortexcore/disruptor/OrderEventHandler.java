package com.kimiha.vortexcore.disruptor;

import com.kimiha.vortexcore.engine.MatchingEngine;
import com.kimiha.vortexcore.engine.OrderBook;
import com.kimiha.vortexcore.engine.OrderBookChangedEvent;
import com.kimiha.vortexcore.engine.TradeResult;
import com.kimiha.vortexcore.model.OrderEntity;
import com.lmax.disruptor.EventHandler;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderEventHandler implements EventHandler<OrderEvent> {
    private final MatchingEngine matchingEngine;
    private final ApplicationEventPublisher eventPublisher;

    public OrderEventHandler(MatchingEngine matchingEngine, ApplicationEventPublisher eventPublisher) {
        this.matchingEngine = matchingEngine;
        this.eventPublisher = eventPublisher; // 可为 null，测试时不推送
    }

    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        OrderEntity order = event.getOrder();
        String securityId = order.getSecurityId();
        OrderBook book = matchingEngine.getOrderBook(securityId);

        if (book.isWashTrading(order)) {
            System.err.println("Blocking Wash Trade: " + order.getClOrderId());
            return;
        }

        List<TradeResult> tradeResults = book.executeMatch(order);
        if (!tradeResults.isEmpty()) {
            for (TradeResult tr : tradeResults) {
                System.out.println("Trade: " + tr.getTakerClOrderId() + " vs " + tr.getMakerClOrderId()
                        + " @ " + tr.getPrice() + " x " + tr.getQty());
            }
        }

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new OrderBookChangedEvent(this, securityId));
        }
    }
}