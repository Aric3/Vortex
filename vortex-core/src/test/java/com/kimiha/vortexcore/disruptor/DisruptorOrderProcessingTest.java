package com.kimiha.vortexcore.disruptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kimiha.vortexcore.engine.MatchingEngine;
import com.kimiha.vortexcore.engine.OrderBook;
import com.kimiha.vortexcore.model.OrderEntity;
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

    private OrderEntity order(String clOrderId, String side, String securityId, String shareholderId, double price, int qty) {
        OrderEntity o = new OrderEntity();
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
        OrderEventHandler handler = new OrderEventHandler(matchingEngine, null, null);
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
            OrderEntity buy = order("ASYNC_B1", "B", "600010", "SH_A", 10.0, 100);
            RingBuffer<OrderEvent> ringBuffer = disruptor.getRingBuffer();
            ringBuffer.publishEvent((event, sequence) -> event.setOrder(buy));

            assertTrue(latch.await(2, TimeUnit.SECONDS), "Handler should process event within 2s");

            OrderBook book = matchingEngine.getOrderBook("600010");
            assertEquals(1, book.getRestingOrderCount());
        } finally {
            disruptor.shutdown();
        }
    }

    @Test
    void publishTwoOppositeOrders_bothProcessed_matchResult() throws InterruptedException {
        MatchingEngine matchingEngine = new MatchingEngine();
        OrderEventHandler handler = new OrderEventHandler(matchingEngine, null, null);
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
            RingBuffer<OrderEvent> ringBuffer = disruptor.getRingBuffer();
            String sec = "600011";

            ringBuffer.publishEvent((event, sequence) -> event.setOrder(
                    order("ASYNC_B2", "B", sec, "SH_A", 10.0, 100)));
            ringBuffer.publishEvent((event, sequence) -> event.setOrder(
                    order("ASYNC_S2", "S", sec, "SH_B", 10.0, 100)));

            assertTrue(latch.await(2, TimeUnit.SECONDS), "Both events should be processed");

            OrderBook book = matchingEngine.getOrderBook(sec);
            assertEquals(0, book.getRestingOrderCount());
        } finally {
            disruptor.shutdown();
        }
    }
}
