package com.kimiha.vortexcore.matching;

import org.springframework.context.ApplicationEvent;

/** 某只标的订单簿发生变动时发布，用于驱动实时推送 */
public class OrderBookChangedEvent extends ApplicationEvent {
    private final String securityId;
    public OrderBookChangedEvent(Object source, String securityId) {
        super(source);
        this.securityId = securityId;
    }

    public String getSecurityId() {
        return securityId;
    }
}
