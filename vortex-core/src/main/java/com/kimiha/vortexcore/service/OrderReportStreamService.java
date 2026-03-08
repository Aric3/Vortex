package com.kimiha.vortexcore.service;

import com.kimiha.vortexcore.model.dto.report.OrderReportEnvelope;
import com.kimiha.vortexcore.model.dto.report.ReportType;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按股东号维护 SSE 订阅，向客户端推送该股东下的订单成交回报、撤单确认回报等
 * 客户端通过 GET /api/v1/vclient/stream/reports?shareholderId=xxx 建立长连接接收推送
 * 使用立即 + 周期心跳避免连接被中间层或浏览器因“无数据”判定为空闲而关闭
 */
@Service
public class OrderReportStreamService {

    private static final Logger log = LogManager.getLogger(OrderReportStreamService.class);

    private static final OrderReportEnvelope HEARTBEAT_ENVELOPE = new OrderReportEnvelope(ReportType.HEARTBEAT, null);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);

    private final ConcurrentHashMap<String, Sinks.Many<OrderReportEnvelope>> sinksByShareholder = new ConcurrentHashMap<>();

    /**
     * 订阅该股东的订单回报流。同一股东可多连接，每条回报会推送给该股东的所有订阅连接。
     * 建立连接后先发一条 HEARTBEAT，之后每 30 秒发一条 HEARTBEAT，避免因长时间无数据导致连接被关闭
     */
    public Flux<OrderReportEnvelope> stream(String shareholderId) {
        if (shareholderId == null || shareholderId.isBlank()) {
            return Flux.error(new IllegalArgumentException("shareholderId required"));
        }
        // autoCancel=false：浏览器刷新会取消订阅，若用默认 true 则 Sink 会被关掉，新连接收不到回报
        Sinks.Many<OrderReportEnvelope> sink = sinksByShareholder.computeIfAbsent(shareholderId,
                k -> Sinks.many().multicast().onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false));
        log.info("[REPORT_STREAM] SSE subscribed shareholderId={} activeSubscribers={}",
                shareholderId, sinksByShareholder.size());
        Flux<OrderReportEnvelope> heartbeats = Flux.concat(
                Flux.just(HEARTBEAT_ENVELOPE),
                Flux.interval(HEARTBEAT_INTERVAL).map(tick -> HEARTBEAT_ENVELOPE)
        );
        return Flux.merge(sink.asFlux(), heartbeats);
    }

    /**
     * 向指定股东推送一条回报（成交、撤单确认等）。
     */
    public void pushReport(String shareholderId, OrderReportEnvelope report) {
        if (shareholderId == null || report == null) return;
        Sinks.Many<OrderReportEnvelope> sink = sinksByShareholder.get(shareholderId);
        if (sink != null) {
            Sinks.EmitResult result = sink.tryEmitNext(report);
            log.debug("[REPORT_STREAM] pushReport shareholderId={} reportType={} emitResult={}",
                    shareholderId, report.getReportType(), result);
            if (result.isFailure()) {
                log.warn("[REPORT_STREAM] pushReport emit failed shareholderId={} reportType={} result={}",
                        shareholderId, report.getReportType(), result);
            }
        } else {
            log.warn("[REPORT_STREAM] pushReport no sink for shareholderId={} reportType={} (SSE not connected for this shareholder?)",
                    shareholderId, report.getReportType());
        }
    }
}
