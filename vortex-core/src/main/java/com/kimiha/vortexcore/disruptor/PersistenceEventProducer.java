package com.kimiha.vortexcore.disruptor;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import org.springframework.stereotype.Component;

@Component
public class PersistenceEventProducer {

    private final RingBuffer<PersistenceEvent> ringBuffer;

    public PersistenceEventProducer(Disruptor<PersistenceEvent> persistenceDisruptor) {
        this.ringBuffer = persistenceDisruptor.getRingBuffer();
    }

    public void publish(PersistenceEventType type, Object payload) {
        ringBuffer.publishEvent((event, sequence) -> {
            event.setType(type);
            event.setPayload(payload);
        });
    }
}
