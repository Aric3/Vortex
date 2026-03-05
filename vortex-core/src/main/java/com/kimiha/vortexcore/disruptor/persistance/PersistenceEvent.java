package com.kimiha.vortexcore.disruptor.persistance;

import lombok.Data;

/**
 * 持久化事件：类型 + 负载，由 PersistenceEventHandler 按类型批写 DB
 */
@Data
public class PersistenceEvent {
    private PersistenceEventType type;
    /** 负载：Order, TradePersistencePayload, OrderReject, CancellationEntity, CancelResultPayload 等 */
    private Object payload;
}
