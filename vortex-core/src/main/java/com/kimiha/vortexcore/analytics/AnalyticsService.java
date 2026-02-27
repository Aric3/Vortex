package com.kimiha.vortexcore.analytics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final AnalyticsProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final AtomicReference<AnalyticsMetrics> cache;

    public AnalyticsService(AnalyticsProperties properties, JdbcTemplate jdbcTemplate) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.cache = new AtomicReference<>(new AnalyticsMetrics(0, 0, null, List.of(), 0));
    }

    public AnalyticsMetrics getMetrics() {
        return cache.get();
    }

    @Scheduled(fixedDelayString = "${analytics.refresh-interval-ms:1000}")
    public void refresh() {
        if (!properties.isEnabled()) {
            return;
        }

        try {
            WashMetrics wash = queryWashMetrics();
            List<LatencyBucket> buckets = queryLatencyBuckets();
            cache.set(new AnalyticsMetrics(
                    wash.washRejects,
                    wash.totalOrders,
                    wash.washRatio,
                    buckets,
                    System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.warn("Analytics refresh failed", e);
        }
    }

    private WashMetrics queryWashMetrics() {
        // 对敲占比分母 = 通过订单 + 拒绝订单。
        // orders 存通过订单，被拒绝订单在 order_rejects。
        String sql = """
                SELECT
                  (SELECT COUNT(*) FROM order_rejects WHERE reject_code = 4001) AS wash_rejects,
                  (
                    (SELECT COUNT(*) FROM orders) +
                    (SELECT COUNT(*) FROM order_rejects)
                  ) AS total_orders
                """;
        return queryWashMetricsBySql(sql);
    }

    private WashMetrics queryWashMetricsBySql(String sql) {
        return jdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return new WashMetrics(0, 0, null);
            }
            long washRejects = rs.getLong("wash_rejects");
            long totalOrders = rs.getLong("total_orders");
            Double washRatio = null;
            if (totalOrders > 0) {
                washRatio = Math.round((washRejects * 1.0 / totalOrders) * 1_000_000d) / 1_000_000d;
            }
            return new WashMetrics(washRejects, totalOrders, washRatio);
        });
    }

    private List<LatencyBucket> queryLatencyBuckets() {
        // 延时定义：首笔成交时间 - 下单时间（毫秒）。
        // 当前统计基于 orders + trades 表。
        String sql = """
                WITH order_trade AS (
                  SELECT taker_cl_order_id AS cl_order_id, trade_time FROM trades
                  UNION ALL
                  SELECT maker_cl_order_id AS cl_order_id, trade_time FROM trades
                ),
                first_trade AS (
                  SELECT cl_order_id, MIN(trade_time) AS first_trade_time
                  FROM order_trade
                  WHERE cl_order_id IS NOT NULL AND cl_order_id <> ''
                  GROUP BY cl_order_id
                ),
                latency AS (
                  SELECT
                    o.cl_order_id,
                    CAST(ft.first_trade_time AS INTEGER) - CAST(o.create_time AS INTEGER) AS latency_ms
                  FROM orders o
                  JOIN first_trade ft ON o.cl_order_id = ft.cl_order_id
                )
                SELECT
                  CASE
                    WHEN latency_ms <= 1 THEN '0-1ms'
                    WHEN latency_ms <= 5 THEN '2-5ms'
                    WHEN latency_ms <= 10 THEN '6-10ms'
                    WHEN latency_ms <= 50 THEN '11-50ms'
                    WHEN latency_ms <= 100 THEN '51-100ms'
                    WHEN latency_ms <= 500 THEN '101-500ms'
                    ELSE '>500ms'
                  END AS bucket,
                  COUNT(*) AS cnt
                FROM latency
                WHERE latency_ms IS NOT NULL AND latency_ms >= 0
                GROUP BY 1
                ORDER BY
                  CASE bucket
                    WHEN '0-1ms' THEN 1
                    WHEN '2-5ms' THEN 2
                    WHEN '6-10ms' THEN 3
                    WHEN '11-50ms' THEN 4
                    WHEN '51-100ms' THEN 5
                    WHEN '101-500ms' THEN 6
                    ELSE 7
                  END
                """;
        return queryLatencyBySql(sql);
    }

    private List<LatencyBucket> queryLatencyBySql(String sql) {
        return jdbcTemplate.query(sql, rs -> {
            List<LatencyBucket> buckets = new ArrayList<>();
            while (rs.next()) {
                buckets.add(new LatencyBucket(
                        rs.getString("bucket"),
                        rs.getLong("cnt")));
            }
            return buckets;
        });
    }

    private static class WashMetrics {
        private final long washRejects;
        private final long totalOrders;
        private final Double washRatio;

        private WashMetrics(long washRejects, long totalOrders, Double washRatio) {
            this.washRejects = washRejects;
            this.totalOrders = totalOrders;
            this.washRatio = washRatio;
        }
    }
}
