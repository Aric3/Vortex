package com.kimiha.vortexcore.model;

import com.kimiha.vortexcore.model.domain.Order;

import java.time.LocalDateTime;

/**
 * 轻量订单状态机：校验合法流转并执行 transition（设置 status/updatedTime）
 */
public final class OrderStateMachine {

    /**
     * 是否允许从 from 转到 to
     */
    public static boolean canTransition(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) return false;
        return switch (from) {
            case New -> to == OrderStatus.PartiallyFilled || to == OrderStatus.Filled || to == OrderStatus.Canceled;
            case PartiallyFilled -> to == OrderStatus.Filled || to == OrderStatus.Canceled;
            case Filled, Canceled, Rejected -> false;
        };
    }

    /**
     * 将订单转到目标状态并更新 updatedTime；不校验（用于引擎内部已确定合法的场景）
     */
    public static void transition(Order order, OrderStatus to) {
        if (order == null || to == null) return;
        order.setStatus(to);
        order.setUpdatedTime(LocalDateTime.now());
    }

    /**
     * 是否可撤单（仅 New 或 PartiallyFilled 可撤）
     */
    public static boolean canCancel(OrderStatus status) {
        return status == OrderStatus.New || status == OrderStatus.PartiallyFilled;
    }

    private OrderStateMachine() {}
}
