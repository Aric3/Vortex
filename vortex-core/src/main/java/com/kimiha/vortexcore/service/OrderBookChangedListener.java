package com.kimiha.vortexcore.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.kimiha.vortexcore.matching.OrderBookChangedEvent;

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
