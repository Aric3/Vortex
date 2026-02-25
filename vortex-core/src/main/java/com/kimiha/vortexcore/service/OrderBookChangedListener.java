package com.kimiha.vortexcore.service;

import com.kimiha.vortexcore.engine.OrderBookChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class OrderBookChangedListener {

    private final OrderBookStreamService streamService;

    public OrderBookChangedListener(OrderBookStreamService streamService) {
        this.streamService = streamService;
    }

    @EventListener
    public void onOrderBookChanged(OrderBookChangedEvent event) {
        streamService.onOrderBookChanged(event.getSecurityId());
    }
}
