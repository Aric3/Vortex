package com.kimiha.vortexcore.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kimiha.vortexcore.model.OrderStatus;
import com.kimiha.vortexcore.model.domain.Order;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 订单查询/列表响应
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderResponse(
    Long id,
    String clOrderId,
    String market,
    String securityId,
    String side,
    Integer qty,
    Double price,
    String shareholderId,
    Integer orderQty,
    Integer cumQty,
    OrderStatus status,
    LocalDateTime createTime,
    LocalDateTime updatedTime
) {
    public static OrderResponse fromEntity(com.kimiha.vortexcore.model.entity.OrderEntity e) {
        if (e == null) return null;
        return new OrderResponse(
            e.getId(), e.getClOrderId(), e.getMarket(), e.getSecurityId(), e.getSide(),
            e.getQty(), e.getPrice(), e.getShareholderId(), e.getOrderQty(), e.getCumQty(),
            e.getStatus(),
            toLocalDateTime(e.getCreateTimeEpochMs()),
            toLocalDateTime(e.getUpdatedTimeEpochMs())
        );
    }

    public static OrderResponse fromOrder(Order o) {
        if (o == null) return null;
        return new OrderResponse(
            null, o.getClOrderId(), o.getMarket(), o.getSecurityId(), o.getSide(),
            o.getQty(), o.getPrice(), o.getShareholderId(), o.getOrderQty(), o.getCumQty(),
            o.getStatus(),
            toLocalDateTime(o.getCreateTimeEpochMs()),
            toLocalDateTime(o.getUpdatedTimeEpochMs())
        );
    }

    private static LocalDateTime toLocalDateTime(Long epochMs) {
        if (epochMs == null || epochMs == 0L) return null;
        return Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
