package com.kimiha.vortexcore.disruptor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kimiha.vortexcore.engine.MatchingEngine;
import com.kimiha.vortexcore.engine.OrderBook;
import com.kimiha.vortexcore.model.OrderEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 同步测试：直接调用 Handler.onEvent，不经过 Disruptor 异步。
 * 验证「事件被消费时」的风控与撮合逻辑是否正确。
 */
class OrderEventHandlerTest {
    private MatchingEngine matchingEngine;
    private OrderEventHandler handler;

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

    private void publishToHandler(OrderEntity order) {
        OrderEvent event = new OrderEvent();
        event.setOrder(order);
        handler.onEvent(event, 0, true);
    }

    @BeforeEach
    void setUp() {
        matchingEngine = new MatchingEngine();
        handler = new OrderEventHandler(matchingEngine, null);
    }

    @Test
    void validBuyOrder_restsOnBook() {
        String sec = "600000";
        OrderEntity buy = order("B001", "B", sec, "SH_A", 10.0, 100);
        publishToHandler(buy);

        OrderBook book = matchingEngine.getOrderBook(sec);
        assertEquals(1, book.getRestingOrderCount());
    }

    @Test
    void oppositeOrders_samePrice_matchAndReduceRestingCount() {
        String sec = "600001";
        OrderEntity buy = order("B002", "B", sec, "SH_A", 10.0, 100);
        OrderEntity sell = order("S002", "S", sec, "SH_B", 10.0, 100);

        publishToHandler(buy);
        OrderBook book = matchingEngine.getOrderBook(sec);
        assertEquals(1, book.getRestingOrderCount());

        publishToHandler(sell);
        assertEquals(0, book.getRestingOrderCount());
    }

    @Test
    void washTrade_buyWhenSameShareholderHasAsk_blocked() {
        String sec = "600002";
        OrderEntity sell = order("S003", "S", sec, "SH_X", 10.0, 100);
        publishToHandler(sell);
        OrderBook book = matchingEngine.getOrderBook(sec);
        assertEquals(1, book.getRestingOrderCount());

        // 同一股东再下买单，且买价 >= 自己的卖挂单价 → 对敲，应被拦截
        OrderEntity buy = order("B003", "B", sec, "SH_X", 10.0, 50);
        publishToHandler(buy);
        // 订单不应进入订单簿，挂单数仍为 1（只有之前的卖单）
        assertEquals(1, book.getRestingOrderCount());
    }

    @Test
    void washTrade_sellWhenSameShareholderHasBid_blocked() {
        String sec = "600003";
        OrderEntity buy = order("B004", "B", sec, "SH_Y", 10.0, 100);
        publishToHandler(buy);

        OrderEntity sell = order("S004", "S", sec, "SH_Y", 10.0, 50);
        publishToHandler(sell);

        OrderBook book = matchingEngine.getOrderBook(sec);
        // 卖单被拦截，只有原来的买单在簿
        assertEquals(1, book.getRestingOrderCount());
    }

    @Test
    void differentShareholders_samePrice_notWashTrade() {
        String sec = "600004";
        OrderEntity sell = order("S005", "S", sec, "SH_A", 10.0, 100);
        publishToHandler(sell);

        OrderEntity buy = order("B005", "B", sec, "SH_B", 10.0, 100);
        publishToHandler(buy);

        OrderBook book = matchingEngine.getOrderBook(sec);
        assertEquals(0, book.getRestingOrderCount());
    }
}
