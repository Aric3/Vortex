package com.kimiha.vortexcore.disruptor.order;

import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;

import java.util.ArrayList;
import java.util.List;

import com.lmax.disruptor.RingBuffer;

/**
 * 持有 N 个分片 Disruptor 及其 RingBuffer 列表；OrderService 按 securityId 直投对应分片，生命周期由 DistruptorManager 管理。
 */
public class ShardDisruptorHolder {

    private static final int SHARD_BUFFER_SIZE = 1024 * 4;

    private final List<Disruptor<OrderEvent>> shardDisruptors;
    private final List<RingBuffer<OrderEvent>> shardRingBuffers;

    public ShardDisruptorHolder(int shardCount, OrderEventHandler orderEventHandler) {
        if (shardCount < 1) {
            throw new IllegalArgumentException("shardCount must be >= 1");
        }
        this.shardDisruptors = new ArrayList<>(shardCount);
        this.shardRingBuffers = new ArrayList<>(shardCount);
        for (int i = 0; i < shardCount; i++) {
            Disruptor<OrderEvent> disruptor = new Disruptor<>(
                    OrderEvent::new,
                    SHARD_BUFFER_SIZE,
                    DaemonThreadFactory.INSTANCE,
                    ProducerType.MULTI,
                    new YieldingWaitStrategy()
            );
            disruptor.handleEventsWith(orderEventHandler);
            disruptor.start();
            shardDisruptors.add(disruptor);
            shardRingBuffers.add(disruptor.getRingBuffer());
        }
    }

    public List<Disruptor<OrderEvent>> getShardDisruptors() {
        return shardDisruptors;
    }

    public List<RingBuffer<OrderEvent>> getShardRingBuffers() {
        return shardRingBuffers;
    }
}
