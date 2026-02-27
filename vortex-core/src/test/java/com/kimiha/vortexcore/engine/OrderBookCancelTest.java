package com.kimiha.vortexcore.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kimiha.vortexcore.Utils;
import com.kimiha.vortexcore.model.OrderStatus;
import com.kimiha.vortexcore.model.domain.Order;
import com.kimiha.vortexcore.model.entity.CancellationEntity;
import org.junit.jupiter.api.Test;

/**
 * 验证 OrderBook 撤单逻辑：撤单成功、撤单不存在、撤单后唯一性释放、同价档多笔撤单等。
 */
class OrderBookCancelTest {

    private static Order order(String clOrderId, String side, double price, int qty) {
        Order o = new Order();
        o.setClOrderId(clOrderId);
        o.setMarket("XSHG");
        o.setSecurityId("600000");
        o.setSide(side);
        o.setShareholderId("SH_A");
        o.setPrice(price);
        o.setQty(qty);
        o.setOrderQty(qty);
        o.setCumQty(0);
        o.setStatus(OrderStatus.New);
        return o;
    }

    private static CancellationEntity cancelReq(String origClOrderId, String side) {
        CancellationEntity c = new CancellationEntity();
        c.setOrigClOrderId(origClOrderId);
        c.setSide(side);
        return c;
    }

    @Test
    void cancelRestingOrder_success_returnsOrderAndRemovesFromBook() {
        OrderBook book = new OrderBook("600000");
        String clOrderId = Utils.randomClOrderId();
        Order resting = order(clOrderId, "B", 10.0, 100);
        book.executeMatch(resting);
        assertEquals(1, book.getRestingOrderCount());

        Order removed = book.cancelByClOrderId(cancelReq(clOrderId, "B"));

        assertNotNull(removed);
        assertEquals(clOrderId, removed.getClOrderId());
        assertEquals(OrderStatus.Canceled, removed.getStatus());
        assertEquals(100, removed.getQty());
        assertEquals(10.0, removed.getPrice());
        assertEquals(0, book.getRestingOrderCount());
    }

    @Test
    void cancelNonExistentOrder_returnsNull() {
        OrderBook book = new OrderBook("600000");
        Order resting = order(Utils.randomClOrderId(), "S", 10.0, 50);
        book.executeMatch(resting);
        assertEquals(1, book.getRestingOrderCount());

        Order removed = book.cancelByClOrderId(cancelReq("NONEXISTENT", "S"));

        assertNull(removed);
        assertEquals(1, book.getRestingOrderCount());
    }

    @Test
    void cancelWrongSide_returnsNull() {
        OrderBook book = new OrderBook("600000");
        String clOrderId = Utils.randomClOrderId();
        Order resting = order(clOrderId, "B", 10.0, 100);
        book.executeMatch(resting);

        // 买单挂在买盘，用卖盘 side 撤单会查错边
        Order removed = book.cancelByClOrderId(cancelReq(clOrderId, "S"));

        assertNull(removed);
        assertEquals(1, book.getRestingOrderCount());
    }

    @Test
    void afterCancel_sameClOrderIdCanBeReused() {
        OrderBook book = new OrderBook("600000");
        String clOrderId = Utils.randomClOrderId();
        Order resting = order(clOrderId, "B", 10.0, 100);
        book.executeMatch(resting);
        assertTrue(book.illegalClOrderId(resting)); // 已在簿，重复使用不合法

        book.cancelByClOrderId(cancelReq(clOrderId, "B"));

        // 撤单后 clOrderId 已释放，再次使用应视为合法（未在簿）
        Order newOrder = order(clOrderId, "S", 10.0, 50);
        assertFalse(book.illegalClOrderId(newOrder));
        book.executeMatch(newOrder);
        assertEquals(1, book.getRestingOrderCount());
    }

    @Test
    void cancelOneOfMultipleAtSamePrice_othersRemain() {
        OrderBook book = new OrderBook("600000");
        String cl1 = Utils.randomClOrderId();
        String cl2 = Utils.randomClOrderId();
        String cl3 = Utils.randomClOrderId();
        book.executeMatch(order(cl1, "B", 10.0, 100));
        book.executeMatch(order(cl2, "B", 10.0, 200));
        book.executeMatch(order(cl3, "B", 10.0, 300));
        assertEquals(3, book.getRestingOrderCount());

        Order removed = book.cancelByClOrderId(cancelReq(cl2, "B"));

        assertNotNull(removed);
        assertEquals(cl2, removed.getClOrderId());
        assertEquals(200, removed.getQty());
        assertEquals(2, book.getRestingOrderCount());

        OrderBookSnapshot snapshot = book.getSnapshot(5);
        assertEquals(1, snapshot.getBids().size());
        assertEquals(400L, snapshot.getBids().get(0).getTotalQty()); // 100 + 300
        assertEquals(2, snapshot.getBids().get(0).getOrderCount());
    }

    @Test
    void cancelAskOrder_success() {
        OrderBook book = new OrderBook("600000");
        String clOrderId = Utils.randomClOrderId();
        Order resting = order(clOrderId, "S", 11.0, 80);
        book.executeMatch(resting);
        assertEquals(1, book.getRestingOrderCount());

        Order removed = book.cancelByClOrderId(cancelReq(clOrderId, "S"));

        assertNotNull(removed);
        assertEquals(clOrderId, removed.getClOrderId());
        assertEquals("S", removed.getSide());
        assertEquals(11.0, removed.getPrice());
        assertEquals(80, removed.getQty());
        assertEquals(OrderStatus.Canceled, removed.getStatus());
        assertEquals(0, book.getRestingOrderCount());
    }
}
