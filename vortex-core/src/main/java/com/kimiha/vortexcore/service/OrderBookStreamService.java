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
    /** securityId -> 多播 Sink，仅用于推送变动后的快照，不承载首帧 */
    private final ConcurrentHashMap<String, Sinks.Many<OrderBookSnapshot>> sinksBySecurity = new ConcurrentHashMap<>();

    public OrderBookStreamService(MatchingEngine matchingEngine) {
        this.matchingEngine = matchingEngine;
    }

    /**
     * 订阅该标的订单簿流：订阅时立即收到当前快照（在订阅时拍快照，保证首帧最新），之后仅在有变动时推送新快照。
     * 不在 stream() 里向共享 Sink 发首帧，避免新客户端接入时给已有订阅者重复推送同一快照。
     */
    public Flux<OrderBookSnapshot> stream(String securityId) {
        Sinks.Many<OrderBookSnapshot> sink = sinksBySecurity.computeIfAbsent(securityId,
                k -> Sinks.many().multicast().onBackpressureBuffer());
        return Flux.concat(
                Flux.defer(() -> Flux.just(currentSnapshot(securityId))),
                sink.asFlux()
        );
    }

    private OrderBookSnapshot currentSnapshot(String securityId) {
        return matchingEngine.getOrderBook(securityId).getSnapshot(SNAPSHOT_DEPTH);
    }

    public void onOrderBookChanged(String securityId) {
        Sinks.Many<OrderBookSnapshot> sink = sinksBySecurity.get(securityId);
        if (sink == null) return;
        sink.tryEmitNext(currentSnapshot(securityId));
    }
}
