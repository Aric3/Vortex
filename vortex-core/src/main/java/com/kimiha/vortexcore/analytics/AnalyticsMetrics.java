package com.kimiha.vortexcore.analytics;

import java.util.List;

/**
 * /api/v1/analytics/metrics 接口返回的指标快照。
 */
public record AnalyticsMetrics(
        long washRejects,
        long totalOrders,
        Double washRatio,
        List<LatencyBucket> latencyBuckets,
        long timestamp
) {}
