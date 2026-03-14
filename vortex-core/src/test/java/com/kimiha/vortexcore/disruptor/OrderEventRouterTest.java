package com.kimiha.vortexcore.disruptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kimiha.vortexcore.disruptor.order.OrderShardRouter;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 路由一致性：同一 securityId 始终落入同一分片；null/空 securityId 落入 0 分片；分片下标在 [0, N)。
 */
class OrderEventRouterTest {

    @Test
    void sameSecurityId_sameShardIndex() {
        int n = 4;
        for (String securityId : List.of("600001", "600002", "600519", "000001")) {
            int a = OrderShardRouter.shardIndex(securityId, n);
            int b = OrderShardRouter.shardIndex(securityId, n);
            assertEquals(a, b, "same securityId must yield same shardIndex");
        }
    }

    @Test
    void shardIndex_inRange() {
        int n = 8;
        for (String securityId : List.of("600001", "600002", "000001", "")) {
            int idx = OrderShardRouter.shardIndex(securityId, n);
            assertTrue(idx >= 0 && idx < n, "shardIndex in [0, " + n + ")");
        }
    }

    @Test
    void nullOrEmptySecurityId_mapsToShardZero() {
        int n = 4;
        assertEquals(0, OrderShardRouter.shardIndex(null, n));
        assertEquals(0, OrderShardRouter.shardIndex("", n));
    }

    @Test
    void orderAndCancel_sameSecurityId_sameShard() {
        int n = 4;
        String securityId = "600100";
        assertEquals(OrderShardRouter.shardIndex(securityId, n), OrderShardRouter.shardIndex(securityId, n),
                "order and cancel for same securityId must use same shard (routing key is securityId)");
    }
}
