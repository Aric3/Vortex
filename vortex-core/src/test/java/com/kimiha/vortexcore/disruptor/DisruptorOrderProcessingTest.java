package com.kimiha.vortexcore.disruptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kimiha.vortexcore.Utils;
import com.kimiha.vortexcore.disruptor.order.OrderEvent;
import com.kimiha.vortexcore.disruptor.order.OrderEventHandler;
import com.kimiha.vortexcore.disruptor.order.OrderEventRouter;
import com.kimiha.vortexcore.disruptor.order.ShardDisruptorHolder;
import com.kimiha.vortexcore.matching.MatchingEngine;
import com.kimiha.vortexcore.matching.OrderBook;
import com.kimiha.vortexcore.model.domain.Order;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * 异步集成测试：使用真实 Disruptor，发布事件后通过 CountDownLatch 等待消费完成再断言。
 * 验证「发布 → 异步消费」整条链路。
 */
class DisruptorOrderProcessingTest {

    private Order order(String clOrderId, String side, String securityId, String shareholderId, double price, int qty) {
        Order o = new Order();
        o.setClOrderId(clOrderId);
        o.setMarket("XSHG");
        o.setSecurityId(securityId);
        o.setSide(side);
        o.setShareholderId(shareholderId);
        o.setPrice(price);
        o.setQty(qty);
        return o;
    }

    @Test
    void publishEvent_asyncHandlerProcesses_orderRestsOnBook() throws InterruptedException {
        MatchingEngine matchingEngine = new MatchingEngine();
        OrderEventHandler handler = new OrderEventHandler(matchingEngine, null, null, null, null, false, 0.02);
        CountDownLatch latch = new CountDownLatch(1);

        int bufferSize = 16;
        Disruptor<OrderEvent> disruptor = new Disruptor<>(
                OrderEvent::new,
                bufferSize,
                DaemonThreadFactory.INSTANCE,
                ProducerType.SINGLE,
                new YieldingWaitStrategy()
        );
        disruptor.handleEventsWith((event, sequence, endOfBatch) -> {
            handler.onEvent(event, sequence, endOfBatch);
            latch.countDown();
        });
        disruptor.start();

        try {
            String sec = "600010";
            Order buy = order(Utils.randomClOrderId(), "B", sec, Utils.randomShareholderId(), 10.0, 100);
            RingBuffer<OrderEvent> ringBuffer = disruptor.getRingBuffer();
            ringBuffer.publishEvent((event, sequence) -> event.setOrder(buy));

            assertTrue(latch.await(2, TimeUnit.SECONDS), "Handler should process event within 2s");

            OrderBook book = matchingEngine.getOrderBook(sec);
            assertEquals(1, book.getRestingOrderCount());
        } finally {
            disruptor.shutdown();
        }
    }

    @Test
    void publishTwoOppositeOrders_bothProcessed_matchResult() throws InterruptedException {
        MatchingEngine matchingEngine = new MatchingEngine();
        OrderEventHandler handler = new OrderEventHandler(matchingEngine, null, null, null, null, false, 0.02);
        CountDownLatch latch = new CountDownLatch(2);

        int bufferSize = 16;
        Disruptor<OrderEvent> disruptor = new Disruptor<>(
                OrderEvent::new,
                bufferSize,
                DaemonThreadFactory.INSTANCE,
                ProducerType.SINGLE,
                new YieldingWaitStrategy()
        );
        disruptor.handleEventsWith((event, sequence, endOfBatch) -> {
            handler.onEvent(event, sequence, endOfBatch);
            latch.countDown();
        });
        disruptor.start();

        try {
            String sec = "600011";
            Order buy = order(Utils.randomClOrderId(), "B", sec, Utils.randomShareholderId(), 10.0, 100);
            Order sell = order(Utils.randomClOrderId(), "S", sec, Utils.randomShareholderId(), 10.0, 100);
            RingBuffer<OrderEvent> ringBuffer = disruptor.getRingBuffer();
            ringBuffer.publishEvent((event, sequence) -> event.setOrder(buy));
            ringBuffer.publishEvent((event, sequence) -> event.setOrder(sell));

            assertTrue(latch.await(2, TimeUnit.SECONDS), "Both events should be processed");

            OrderBook book = matchingEngine.getOrderBook(sec);
            assertEquals(0, book.getRestingOrderCount());
        } finally {
            disruptor.shutdown();
        }
    }

    /** N=1 管道：主 Disruptor -> Router -> 单分片 -> OrderEventHandler，行为与单消费者一致。 */
    @Test
    void routerWithOneShard_orderProcessed_orderRestsOnBook() throws InterruptedException {
        MatchingEngine matchingEngine = new MatchingEngine();
        OrderEventHandler handler = new OrderEventHandler(matchingEngine, null, null, null, null, false, 0.02);
        ShardDisruptorHolder holder = new ShardDisruptorHolder(1, handler);
        OrderEventRouter router = new OrderEventRouter(holder.getShardRingBuffers());

        int bufferSize = 16;
        Disruptor<OrderEvent> mainDisruptor = new Disruptor<>(
                OrderEvent::new,
                bufferSize,
                DaemonThreadFactory.INSTANCE,
                ProducerType.SINGLE,
                new YieldingWaitStrategy()
        );
        mainDisruptor.handleEventsWith(router);
        mainDisruptor.start();

        try {
            String sec = "600020";
            Order buy = order(Utils.randomClOrderId(), "B", sec, Utils.randomShareholderId(), 10.0, 100);
            RingBuffer<OrderEvent> mainBuffer = mainDisruptor.getRingBuffer();
            mainBuffer.publishEvent((event, sequence) -> event.setOrder(buy));

            Thread.sleep(300);

            OrderBook book = matchingEngine.getOrderBook(sec);
            assertEquals(1, book.getRestingOrderCount(), "N=1 pipeline: order should rest on book");
        } finally {
            mainDisruptor.shutdown();
            for (var d : holder.getShardDisruptors()) {
                d.shutdown();
            }
        }
    }

    /** 多分片：不同 securityId 的订单进入不同分片，各自订单簿正确。 */
    @Test
    void routerWithMultipleShards_differentSymbols_bothOrdersOnBooks() throws InterruptedException {
        MatchingEngine matchingEngine = new MatchingEngine();
        OrderEventHandler handler = new OrderEventHandler(matchingEngine, null, null, null, null, false, 0.02);
        ShardDisruptorHolder holder = new ShardDisruptorHolder(4, handler);
        OrderEventRouter router = new OrderEventRouter(holder.getShardRingBuffers());

        int bufferSize = 16;
        Disruptor<OrderEvent> mainDisruptor = new Disruptor<>(
                OrderEvent::new,
                bufferSize,
                DaemonThreadFactory.INSTANCE,
                ProducerType.SINGLE,
                new YieldingWaitStrategy()
        );
        mainDisruptor.handleEventsWith(router);
        mainDisruptor.start();

        try {
            String sec1 = "600001";
            String sec2 = "600002";
            Order buy1 = order(Utils.randomClOrderId(), "B", sec1, Utils.randomShareholderId(), 10.0, 100);
            Order buy2 = order(Utils.randomClOrderId(), "B", sec2, Utils.randomShareholderId(), 11.0, 200);
            RingBuffer<OrderEvent> mainBuffer = mainDisruptor.getRingBuffer();
            mainBuffer.publishEvent((event, sequence) -> event.setOrder(buy1));
            mainBuffer.publishEvent((event, sequence) -> event.setOrder(buy2));

            Thread.sleep(400);

            assertEquals(1, matchingEngine.getOrderBook(sec1).getRestingOrderCount());
            assertEquals(1, matchingEngine.getOrderBook(sec2).getRestingOrderCount());
        } finally {
            mainDisruptor.shutdown();
            for (var d : holder.getShardDisruptors()) {
                d.shutdown();
            }
        }
    }
}
