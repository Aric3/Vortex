package com.kimiha.vortexcore.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

import jakarta.annotation.PreDestroy;
import com.kimiha.vortexcore.config.AnalyticsProperties;
import com.kimiha.vortexcore.model.AnalyticsEvent;
import com.kimiha.vortexcore.model.AnalyticsMetrics;
import com.kimiha.vortexcore.model.LatencyBucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Service
public class AnalyticsService {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);
    private static final int WASH_REJECT_CODE = 4001;
    private static final List<String> LATENCY_BUCKET_ORDER = List.of(
            "0-1s", "1-5s", "5-10s", "10-30s", "30-60s", "1-5min", "5-15min", ">15min"
    );

    private final AnalyticsProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final AtomicReference<AnalyticsMetrics> cache;
    private final Sinks.Many<AnalyticsMetrics> metricSink;
    private final LongAdder washRejects;
    private final LongAdder totalOrders;
    private final ConcurrentHashMap<String, LongAdder> latencyBucketCounters;
    private final ConcurrentHashMap<String, Long> orderCreateTimeMap;
    private final ConcurrentHashMap<String, Boolean> firstTradeRecorded;
    private final AtomicBoolean bootstrapAttempted;
    private final AtomicBoolean liveEventsSeen;
    private final ExecutorService eventExecutor;

    public AnalyticsService(AnalyticsProperties properties, JdbcTemplate jdbcTemplate) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.cache = new AtomicReference<>(new AnalyticsMetrics(0, 0, null, List.of(), 0));
        this.metricSink = Sinks.many().multicast().onBackpressureBuffer();
        this.washRejects = new LongAdder();
        this.totalOrders = new LongAdder();
        this.latencyBucketCounters = new ConcurrentHashMap<>();
        for (String bucket : LATENCY_BUCKET_ORDER) {
            this.latencyBucketCounters.put(bucket, new LongAdder());
        }
        this.orderCreateTimeMap = new ConcurrentHashMap<>();
        this.firstTradeRecorded = new ConcurrentHashMap<>();
        this.bootstrapAttempted = new AtomicBoolean(false);
        this.liveEventsSeen = new AtomicBoolean(false);
        this.eventExecutor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
    }

    public AnalyticsMetrics getMetrics() {
        return cache.get();
    }

    public Flux<AnalyticsMetrics> streamMetrics() {
        Flux<AnalyticsMetrics> updates = metricSink.asFlux();
        Flux<AnalyticsMetrics> heartbeat = Flux.interval(HEARTBEAT_INTERVAL).map(tick -> cache.get());
        return Flux.concat(
                Flux.defer(() -> Flux.just(cache.get())),
                Flux.merge(updates, heartbeat)
        );
    }

    @Scheduled(fixedDelayString = "${analytics.refresh-interval-ms:1000}")
    public void refresh() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            bootstrapIfNeeded();
            emitSnapshot();
        } catch (Exception e) {
            log.warn("Analytics refresh failed", e);
        }
    }

    @EventListener
    public void onAnalyticsEvent(AnalyticsEvent event) {
        if (!properties.isEnabled() || event == null || event.type() == null) {
            return;
        }
        try {
            eventExecutor.execute(() -> applyEvent(event));
        } catch (RejectedExecutionException ex) {
            applyEvent(event);
        }
    }

    @PreDestroy
    public void shutdown() {
        eventExecutor.shutdown();
        try {
            if (!eventExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                eventExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            eventExecutor.shutdownNow();
        }
    }

    private void applyEvent(AnalyticsEvent event) {
        liveEventsSeen.set(true);
        switch (event.type()) {
            case ORDER_ACCEPTED -> handleOrderAccepted(event);
            case ORDER_REJECTED -> handleOrderRejected(event);
            case TRADE -> handleTrade(event);
        }
    }

    private void handleOrderAccepted(AnalyticsEvent event) {
        totalOrders.increment();
        if (event.clOrderId() != null && event.createTimeEpochMs() != null) {
            orderCreateTimeMap.putIfAbsent(event.clOrderId(), event.createTimeEpochMs());
        }
    }

    private void handleOrderRejected(AnalyticsEvent event) {
        totalOrders.increment();
        if (event.rejectCode() != null && event.rejectCode() == WASH_REJECT_CODE) {
            washRejects.increment();
        }
    }

    private void handleTrade(AnalyticsEvent event) {
        if (event.tradeTimeEpochMs() == null) {
            return;
        }
        recordFirstTradeLatency(event.takerClOrderId(), event.tradeTimeEpochMs());
        recordFirstTradeLatency(event.makerClOrderId(), event.tradeTimeEpochMs());
    }

    private void recordFirstTradeLatency(String clOrderId, long tradeTimeEpochMs) {
        if (clOrderId == null || clOrderId.isBlank()) {
            return;
        }
        if (firstTradeRecorded.putIfAbsent(clOrderId, Boolean.TRUE) != null) {
            return;
        }
        Long createTimeEpochMs = orderCreateTimeMap.get(clOrderId);
        if (createTimeEpochMs == null) {
            return;
        }
        long latencyMs = tradeTimeEpochMs - createTimeEpochMs;
        if (latencyMs < 0) {
            return;
        }
        String bucket = toLatencyBucket(latencyMs);
        latencyBucketCounters.computeIfAbsent(bucket, key -> new LongAdder()).increment();
    }

    private void bootstrapIfNeeded() {
        if (!bootstrapAttempted.compareAndSet(false, true)) {
            return;
        }
        if (liveEventsSeen.get()) {
            return;
        }
        bootstrapFromDatabase();
    }

    private void bootstrapFromDatabase() {
        try {
            Long acceptedOrders = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Long.class);
            Long rejectedOrders = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_rejects", Long.class);
            Long washRejectedOrders = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM order_rejects WHERE reject_code = ?",
                    Long.class,
                    WASH_REJECT_CODE
            );
            if (acceptedOrders != null && acceptedOrders > 0) {
                totalOrders.add(acceptedOrders);
            }
            if (rejectedOrders != null && rejectedOrders > 0) {
                totalOrders.add(rejectedOrders);
            }
            if (washRejectedOrders != null && washRejectedOrders > 0) {
                washRejects.add(washRejectedOrders);
            }

            List<Map<String, Object>> orderRows = jdbcTemplate.queryForList("""
                    SELECT cl_order_id, create_time_epoch_ms
                    FROM orders
                    WHERE cl_order_id IS NOT NULL
                      AND cl_order_id <> ''
                      AND create_time_epoch_ms IS NOT NULL
                    """);
            for (Map<String, Object> row : orderRows) {
                Object clOrderId = row.get("cl_order_id");
                Object createTime = row.get("create_time_epoch_ms");
                if (clOrderId != null && createTime instanceof Number number) {
                    orderCreateTimeMap.put(String.valueOf(clOrderId), number.longValue());
                }
            }

            List<Map<String, Object>> latencyRows = jdbcTemplate.queryForList("""
                    WITH order_trade AS (
                      SELECT taker_cl_order_id AS cl_order_id, trade_time_epoch_ms FROM trades
                      UNION ALL
                      SELECT maker_cl_order_id AS cl_order_id, trade_time_epoch_ms FROM trades
                    ),
                    first_trade AS (
                      SELECT cl_order_id, MIN(trade_time_epoch_ms) AS first_trade_time_epoch_ms
                      FROM order_trade
                      WHERE cl_order_id IS NOT NULL AND cl_order_id <> '' AND trade_time_epoch_ms IS NOT NULL
                      GROUP BY cl_order_id
                    )
                    SELECT
                      o.cl_order_id AS cl_order_id,
                      (ft.first_trade_time_epoch_ms - o.create_time_epoch_ms) AS latency_ms
                    FROM orders o
                    JOIN first_trade ft ON o.cl_order_id = ft.cl_order_id
                    WHERE o.create_time_epoch_ms IS NOT NULL
                    """);
            for (Map<String, Object> row : latencyRows) {
                Object clOrderId = row.get("cl_order_id");
                Object latencyObj = row.get("latency_ms");
                if (!(clOrderId instanceof String clId) || !(latencyObj instanceof Number number)) {
                    continue;
                }
                long latencyMs = number.longValue();
                if (latencyMs < 0) {
                    continue;
                }
                firstTradeRecorded.put(clId, Boolean.TRUE);
                String bucket = toLatencyBucket(latencyMs);
                latencyBucketCounters.computeIfAbsent(bucket, key -> new LongAdder()).increment();
            }
        } catch (Exception e) {
            log.warn("Analytics bootstrap from database failed", e);
        }
    }

    private void emitSnapshot() {
        long washRejectCount = washRejects.sum();
        long totalOrderCount = totalOrders.sum();
        Double washRatio = null;
        if (totalOrderCount > 0) {
            washRatio = Math.round((washRejectCount * 1.0 / totalOrderCount) * 1_000_000d) / 1_000_000d;
        }

        List<LatencyBucket> buckets = new ArrayList<>();
        for (String bucket : LATENCY_BUCKET_ORDER) {
            LongAdder counter = latencyBucketCounters.get(bucket);
            long count = counter == null ? 0L : counter.sum();
            if (count > 0) {
                buckets.add(new LatencyBucket(bucket, count));
            }
        }

        AnalyticsMetrics metrics = new AnalyticsMetrics(
                washRejectCount,
                totalOrderCount,
                washRatio,
                buckets,
                System.currentTimeMillis()
        );
        cache.set(metrics);
        metricSink.tryEmitNext(metrics);
    }

    private String toLatencyBucket(long latencyMs) {
        if (latencyMs <= 1000) {
            return "0-1s";
        }
        if (latencyMs <= 5000) {
            return "1-5s";
        }
        if (latencyMs <= 10000) {
            return "5-10s";
        }
        if (latencyMs <= 30000) {
            return "10-30s";
        }
        if (latencyMs <= 60000) {
            return "30-60s";
        }
        if (latencyMs <= 300000) {
            return "1-5min";
        }
        if (latencyMs <= 900000) {
            return "5-15min";
        }
        return ">15min";
    }
}
