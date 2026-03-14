package com.kimiha.vortexcore.disruptor.order;

/**
 * 按 securityId 计算分片下标，保证同一标的始终落入同一分片，供发布端与测试复用。
 * 与分片 Disruptor 配合可保证「一支股票只有一个消费者」。
 */
public final class OrderShardRouter {

    private OrderShardRouter() {}

    /**
     * @param securityId 标的代码，可为 null（按空串处理）
     * @param shardCount 分片数，必须 &gt; 0
     * @return 分片下标，范围 [0, shardCount)
     */
    public static int shardIndex(String securityId, int shardCount) {
        String s = securityId == null ? "" : securityId;
        return Math.abs(s.hashCode() % shardCount);
    }
}
