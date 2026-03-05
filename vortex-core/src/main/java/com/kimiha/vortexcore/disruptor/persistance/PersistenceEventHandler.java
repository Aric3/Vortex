package com.kimiha.vortexcore.disruptor.persistance;

import com.kimiha.vortexcore.service.PersistenceService;
import com.lmax.disruptor.EventHandler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 消费持久化事件并批写 SQLite；批量交给 PersistenceService 落库
 */
@Component
public class PersistenceEventHandler implements EventHandler<PersistenceEvent> {

    private static final int BATCH_SIZE = 50;

    private final PersistenceService persistenceService;
    private final List<PersistenceEvent> batch = new ArrayList<>(BATCH_SIZE);

    public PersistenceEventHandler(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Override
    public void onEvent(PersistenceEvent event, long sequence, boolean endOfBatch) {
        PersistenceEvent copy = new PersistenceEvent();
        copy.setType(event.getType());
        copy.setPayload(event.getPayload());
        batch.add(copy);
        if (batch.size() >= BATCH_SIZE || endOfBatch) {
            persistenceService.processBatch(new ArrayList<>(batch));
            batch.clear();
        }
    }
}
