package com.kimiha.vortexcore.service;

import com.kimiha.vortexcore.model.dto.OrderReportEnvelope;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 按股东号维护 SSE 订阅，向客户端推送该股东下的订单成交回报、撤单确认回报等。
 * 客户端通过 GET /api/v1/vclient/stream/reports?shareholderId=xxx 建立长连接接收推送。
 */
@Service
public class OrderReportStreamService {

    private final ConcurrentHashMap<String, Sinks.Many<OrderReportEnvelope>> sinksByShareholder = new ConcurrentHashMap<>();

    /**
     * 订阅该股东的订单回报流。同一股东可多连接，每条回报会推送给该股东的所有订阅连接。
     */
    public Flux<OrderReportEnvelope> stream(String shareholderId) {
        if (shareholderId == null || shareholderId.isBlank()) {
            return Flux.error(new IllegalArgumentException("shareholderId required"));
        }
        Sinks.Many<OrderReportEnvelope> sink = sinksByShareholder.computeIfAbsent(shareholderId,
                k -> Sinks.many().multicast().onBackpressureBuffer());
        return sink.asFlux();
    }

    /**
     * 向指定股东推送一条回报（成交、撤单确认等）。
     */
    public void pushReport(String shareholderId, OrderReportEnvelope report) {
        if (shareholderId == null || report == null) return;
        Sinks.Many<OrderReportEnvelope> sink = sinksByShareholder.get(shareholderId);
        if (sink != null) {
            sink.tryEmitNext(report);
        }
    }
}
