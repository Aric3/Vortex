package com.kimiha.vortexcore.analytics;

/**
 * 成交延时分桶项：bucket 为延时区间，count 为该区间内订单数量。
 */
public record LatencyBucket(String bucket, long count) {}
