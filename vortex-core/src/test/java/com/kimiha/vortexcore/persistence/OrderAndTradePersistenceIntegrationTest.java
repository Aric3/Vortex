package com.kimiha.vortexcore.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kimiha.vortexcore.Utils;
import com.kimiha.vortexcore.model.OrderStatus;
import com.kimiha.vortexcore.model.dto.OrderSubmitRequest;
import com.kimiha.vortexcore.model.entity.OrderEntity;
import com.kimiha.vortexcore.model.entity.TradeEntity;
import com.kimiha.vortexcore.model.ProcessOrderResult;
import com.kimiha.vortexcore.repository.OrderRepository;
import com.kimiha.vortexcore.repository.TradeRepository;
import com.kimiha.vortexcore.service.OrderService;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 集成测试：发单后异步持久化，验证 orders / trades 表中有预期数据
 */
@SpringBootTest
class OrderAndTradePersistenceIntegrationTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private TradeRepository tradeRepository;

    private static OrderSubmitRequest order(String clOrderId, String side, String securityId, String shareholderId, double price, int qty) {
        return new OrderSubmitRequest(clOrderId, "XSHG", securityId, side, qty, price, shareholderId);
    }

    @Test
    void submitMatchingOrders_ordersAndTradesPersisted() throws InterruptedException {
        String sec = "600100";
        OrderSubmitRequest buy = order(Utils.randomClOrderId(), "B", sec, Utils.randomShareholderId(), 10.0, 100);
        OrderSubmitRequest sell = order(Utils.randomClOrderId(), "S", sec, Utils.randomShareholderId(), 10.0, 100);

        ProcessOrderResult r1 = orderService.processOrder(buy);
        assertTrue(r1.isSuccess(), "buy: " + r1.getErrorMessage());
        ProcessOrderResult r2 = orderService.processOrder(sell);
        assertTrue(r2.isSuccess(), "sell: " + r2.getErrorMessage());

        // 等待异步持久化（PersistenceEventHandler）
        for (int i = 0; i < 50; i++) {
            TimeUnit.MILLISECONDS.sleep(100);
            if (orderRepository.count() >= 2 && tradeRepository.count() >= 1) {
                break;
            }
        }

        List<OrderEntity> orders = orderRepository.findAll();
        assertTrue(orders.size() >= 2, "expected at least 2 orders persisted");
        List<OrderEntity> filledOrders = orders.stream().filter(o -> o.getStatus() == OrderStatus.Filled).toList();
        assertTrue(filledOrders.size() >= 2, "both orders should be Filled after full match");

        assertTrue(tradeRepository.count() >= 1, "at least one trade should be persisted");
        TradeEntity trade = tradeRepository.findAll().get(0);
        assertEquals(sec, trade.getSecurityId());
        assertEquals(100, trade.getQty());
        assertNotNull(trade.getExecId());
    }
}
