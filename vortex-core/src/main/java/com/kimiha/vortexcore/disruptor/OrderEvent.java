package com.kimiha.vortexcore.disruptor;

import com.kimiha.vortexcore.model.entity.CancellationEntity;
import com.kimiha.vortexcore.model.domain.Order;
import lombok.Data;

@Data
public class OrderEvent {
    /** 下单请求（领域订单） */
    private Order order;
    /** true 表示撤单请求，false 表示下单请求 */
    private boolean cancel;
    /** 撤单请求（仍用 Entity 以兼容持久化；后续可改为 CancelRequest） */
    private CancellationEntity cancellation;
}