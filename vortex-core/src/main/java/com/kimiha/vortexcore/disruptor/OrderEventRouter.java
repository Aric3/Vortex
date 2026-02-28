package com.kimiha.vortexcore.disruptor;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;

import java.util.List;

/**
 * 主 Disruptor 的唯一消费者：按 securityId 将订单事件路由到 N 个分片 RingBuffer 之一，
 * 保证同一标的的订单与撤单进入同一分片，从而订单簿单线程访问、无锁
 */
public class OrderEventRouter implements EventHandler<OrderEvent> {

    private final List<RingBuffer<OrderEvent>> shardBuffers;
    private final int shardCount;

    public OrderEventRouter(List<RingBuffer<OrderEvent>> shardBuffers) {
        this.shardBuffers = List.copyOf(shardBuffers);
        this.shardCount = this.shardBuffers.size();
    }

    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        String securityId = event.isCancel()
                ? event.getCancellation().getSecurityId()
                : event.getOrder().getSecurityId();
        if (securityId == null) {
            securityId = "";
        }
        int shardIndex = Math.abs(securityId.hashCode() % shardCount);
        RingBuffer<OrderEvent> target = shardBuffers.get(shardIndex);
        target.publishEvent((shardEvent, seq) -> {
            shardEvent.setOrder(event.getOrder());
            shardEvent.setCancel(event.isCancel());
            shardEvent.setCancellation(event.getCancellation());
        });
    }
}
