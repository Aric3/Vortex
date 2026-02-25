package com.kimiha.vortexcore.service;

import com.kimiha.vortexcore.engine.MatchingEngine;
import com.kimiha.vortexcore.engine.OrderBookSnapshot;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 按标的维护 SSE 订阅（WebFlux），订单簿变动时向订阅端推送快照。
 */
@Service
public class OrderBookStreamService {
    private static final int SNAPSHOT_DEPTH = 10;

    private final MatchingEngine matchingEngine;
    /** securityId -> 可多播、新订阅者收到最近一次快照 */
    private final ConcurrentHashMap<String, Sinks.Many<OrderBookSnapshot>> sinksBySecurity = new ConcurrentHashMap<>();

    public OrderBookStreamService(MatchingEngine matchingEngine) {
        this.matchingEngine = matchingEngine;
    }

    /**
     * 订阅该标的订单簿流：先收到当前快照，之后每次变动推送新快照。
     */
    public Flux<OrderBookSnapshot> stream(String securityId) {
        Sinks.Many<OrderBookSnapshot> sink = sinksBySecurity.computeIfAbsent(securityId,
                k -> Sinks.many().replay().limit(1));
        OrderBookSnapshot initial = matchingEngine.getOrderBook(securityId).getSnapshot(SNAPSHOT_DEPTH);
        sink.tryEmitNext(initial);
        return sink.asFlux();
    }

    public void onOrderBookChanged(String securityId) {
        Sinks.Many<OrderBookSnapshot> sink = sinksBySecurity.get(securityId);
        if (sink == null) return;
        OrderBookSnapshot snapshot = matchingEngine.getOrderBook(securityId).getSnapshot(SNAPSHOT_DEPTH);
        sink.tryEmitNext(snapshot);
    }
}
