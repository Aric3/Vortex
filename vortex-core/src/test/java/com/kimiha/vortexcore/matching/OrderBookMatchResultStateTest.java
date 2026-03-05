package com.kimiha.vortexcore.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kimiha.vortexcore.Utils;
import com.kimiha.vortexcore.model.OrderStatus;
import com.kimiha.vortexcore.model.domain.Order;
import org.junit.jupiter.api.Test;

/**
 * 验证撮合后订单状态与累计量：MatchResult.ordersUpdated 中 taker/maker 的 status、cumQty 正确
 */
class OrderBookMatchResultStateTest {

    private static Order order(String clOrderId, String side, int qty, double price, int orderQty, int cumQty, OrderStatus status) {
        Order o = new Order();
        o.setClOrderId(clOrderId);
        o.setMarket("XSHG");
        o.setSecurityId("600000");
        o.setSide(side);
        o.setShareholderId("SH_A");
        o.setPrice(price);
        o.setQty(qty);
        o.setOrderQty(orderQty);
        o.setCumQty(cumQty);
        o.setStatus(status);
        return o;
    }

    @Test
    void fullMatch_takerFilled_makerFilled() {
        OrderBook book = new OrderBook("600000");
        Order resting = order(Utils.randomClOrderId(), "S", 100, 10.0, 100, 0, OrderStatus.New);
        book.executeMatch(resting);
        assertEquals(1, book.getRestingOrderCount());

        Order taker = order(Utils.randomClOrderId(), "B", 100, 10.0, 100, 0, OrderStatus.New);
        MatchResult result = book.executeMatch(taker);

        assertEquals(2, result.ordersUpdated().size());
        Order takerUpdated = result.ordersUpdated().stream().filter(o -> taker.getClOrderId().equals(o.getClOrderId())).findFirst().orElseThrow();
        Order makerUpdated = result.ordersUpdated().stream().filter(o -> resting.getClOrderId().equals(o.getClOrderId())).findFirst().orElseThrow();

        assertEquals(OrderStatus.Filled, takerUpdated.getStatus());
        assertEquals(100, takerUpdated.getCumQty());
        assertEquals(0, takerUpdated.getQty());

        assertEquals(OrderStatus.Filled, makerUpdated.getStatus());
        assertEquals(100, makerUpdated.getCumQty());
        assertEquals(0, makerUpdated.getQty());

        assertEquals(1, result.tradeResults().size());
        assertEquals(100, result.tradeResults().get(0).qty());
    }

    @Test
    void partialMatch_takerPartiallyFilled_makerFilled() {
        OrderBook book = new OrderBook("600001");
        Order resting = order(Utils.randomClOrderId(), "S", 50, 10.0, 50, 0, OrderStatus.New);
        book.executeMatch(resting);

        Order taker = order(Utils.randomClOrderId(), "B", 100, 10.0, 100, 0, OrderStatus.New);
        MatchResult result = book.executeMatch(taker);

        assertEquals(2, result.ordersUpdated().size());
        Order takerUpdated = result.ordersUpdated().stream().filter(o -> taker.getClOrderId().equals(o.getClOrderId())).findFirst().orElseThrow();
        Order makerUpdated = result.ordersUpdated().stream().filter(o -> resting.getClOrderId().equals(o.getClOrderId())).findFirst().orElseThrow();

        assertEquals(OrderStatus.PartiallyFilled, takerUpdated.getStatus());
        assertEquals(50, takerUpdated.getCumQty());
        assertEquals(50, takerUpdated.getQty());

        assertEquals(OrderStatus.Filled, makerUpdated.getStatus());
        assertEquals(50, makerUpdated.getCumQty());
    }

    /** 新订单完全没成交、直接入簿，应保持 New，不应被标成 PartiallyFilled */
    @Test
    void noMatch_directlyResting_remainsNew() {
        OrderBook book = new OrderBook("600002");
        // 先挂一笔卖 10.0
        Order resting = order(Utils.randomClOrderId(), "S", 100, 10.0, 100, 0, OrderStatus.New);
        book.executeMatch(resting);

        // 买 9.0 无法成交，直接入簿
        Order taker = order(Utils.randomClOrderId(), "B", 50, 9.0, 50, 0, OrderStatus.New);
        MatchResult result = book.executeMatch(taker);

        assertEquals(1, result.ordersUpdated().size());
        Order takerUpdated = result.ordersUpdated().stream().filter(o -> taker.getClOrderId().equals(o.getClOrderId())).findFirst().orElseThrow();
        assertEquals(OrderStatus.New, takerUpdated.getStatus());
        assertEquals(0, takerUpdated.getCumQty());
        assertEquals(50, takerUpdated.getQty());
        assertEquals(0, result.tradeResults().size());
        assertEquals(2, book.getRestingOrderCount());
    }
}
