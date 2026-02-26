package com.kimiha.vortexcore.disruptor;

public enum PersistenceEventType {
    ORDER_ACCEPTED,
    ORDER_UPDATED,
    TRADE,
    ORDER_REJECT,
    CANCEL_REQUEST,
    CANCEL_CONFIRMED,
    CANCEL_REJECTED
}
