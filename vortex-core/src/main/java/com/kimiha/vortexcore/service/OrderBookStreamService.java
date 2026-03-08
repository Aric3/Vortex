package com.kimiha.vortexcore.service;

import org.springframework.stereotype.Service;

import com.kimiha.vortexcore.matching.MatchingEngine;
import com.kimiha.vortexcore.matching.OrderBookSnapshot;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按标的维护 SSE 订阅（WebFlux），订单簿变动时向订阅端推送快照。
 * 每个订阅在 stream(securityId, depth) 时传入 depth，之后该订阅的首帧与每次变动推送都使用该 depth 取前 depth 档挂单。
 */
@Service
public class OrderBookStreamService {

    private final MatchingEngine matchingEngine;
    /** securityId -> 多播 Sink，仅发送「变动」信号，不携带快照；各订阅用各自的 depth 在收到信号时拍快照 */
    private final ConcurrentHashMap<String, Sinks.Many<Object>> sinksBySecurity = new ConcurrentHashMap<>();

    private static final Object CHANGE_SIGNAL = new Object();
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);

    public OrderBookStreamService(MatchingEngine matchingEngine) {
        this.matchingEngine = matchingEngine;
    }

    /**
     * 订阅该标的订单簿流：订阅时传入 depth，立即收到当前快照（前 depth 档），之后每次订单簿变动或每 30 秒推送一次快照（保活）
     */
    public Flux<OrderBookSnapshot> stream(String securityId, int depth) {
        // autoCancel=false：刷新后新连接仍能收到订单簿变动，否则 Sink 在上一连接关闭后即失效
        Sinks.Many<Object> sink = sinksBySecurity.computeIfAbsent(securityId,
                k -> Sinks.many().multicast().onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false));
        Flux<OrderBookSnapshot> onChange = sink.asFlux().map(ignore -> currentSnapshot(securityId, depth));
        Flux<OrderBookSnapshot> heartbeat = Flux.interval(HEARTBEAT_INTERVAL).map(tick -> currentSnapshot(securityId, depth));
        return Flux.concat(
                Flux.defer(() -> Flux.just(currentSnapshot(securityId, depth))),
                Flux.merge(onChange, heartbeat)
        );
    }

    private OrderBookSnapshot currentSnapshot(String securityId, int depth) {
        return matchingEngine.getOrderBook(securityId).getPublishedSnapshot(depth);
    }

    /**
     * 订单簿变动时调用，向该标的所有订阅者推送一次快照（各订阅者用自己订阅时的 depth）。
     */
    public void onOrderBookChanged(String securityId) {
        Sinks.Many<Object> sink = sinksBySecurity.get(securityId);
        if (sink == null) return;
        sink.tryEmitNext(CHANGE_SIGNAL);
    }
}
