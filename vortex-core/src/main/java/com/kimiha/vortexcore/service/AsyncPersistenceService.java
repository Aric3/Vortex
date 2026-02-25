package com.kimiha.vortexcore.service;

import com.kimiha.vortexcore.model.OrderEventEntity;
import com.kimiha.vortexcore.model.TradeFillEntity;
import com.kimiha.vortexcore.repository.OrderEventRepository;
import com.kimiha.vortexcore.repository.TradeFillRepository;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncPersistenceService {
    private final OrderEventRepository orderEventRepository;
    private final TradeFillRepository tradeFillRepository;

    public AsyncPersistenceService(
            OrderEventRepository orderEventRepository,
            TradeFillRepository tradeFillRepository) {
        this.orderEventRepository = orderEventRepository;
        this.tradeFillRepository = tradeFillRepository;
    }

    @Async
    public void saveOrderEvent(OrderEventEntity event) {
        orderEventRepository.save(event);
    }

    @Async
    public void saveTradeFill(TradeFillEntity fill) {
        tradeFillRepository.save(fill);
    }
}
