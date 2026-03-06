package com.kimiha.vortexcore.model;

/**
 * Analytics 内存聚合事件。
 */
public record AnalyticsEvent(
        Type type,
        String clOrderId,
        Long createTimeEpochMs,
        Integer rejectCode,
        Long tradeTimeEpochMs,
        String takerClOrderId,
        String makerClOrderId
) {
    public enum Type {
        ORDER_ACCEPTED,
        ORDER_REJECTED,
        TRADE
    }

    public static AnalyticsEvent orderAccepted(String clOrderId, Long createTimeEpochMs) {
        return new AnalyticsEvent(Type.ORDER_ACCEPTED, clOrderId, createTimeEpochMs, null, null, null, null);
    }

    public static AnalyticsEvent orderRejected(Integer rejectCode) {
        return new AnalyticsEvent(Type.ORDER_REJECTED, null, null, rejectCode, null, null, null);
    }

    public static AnalyticsEvent trade(Long tradeTimeEpochMs, String takerClOrderId, String makerClOrderId) {
        return new AnalyticsEvent(Type.TRADE, null, null, null, tradeTimeEpochMs, takerClOrderId, makerClOrderId);
    }
}
