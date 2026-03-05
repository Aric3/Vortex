package com.kimiha.vortexcore.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kimiha.vortexcore.model.entity.OrderEntity;
import com.kimiha.vortexcore.disruptor.order.OrderEvent;
import com.kimiha.vortexcore.model.ValidationResult;
import com.kimiha.vortexcore.model.domain.Order;
import com.kimiha.vortexcore.repository.OrderRepository;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("unchecked")
class TradingServiceValidationTest {
    private OrderService tradingService;

    private OrderEntity validOrder(String clOrderId) {
        OrderEntity order = new OrderEntity();
        order.setClOrderId(clOrderId);
        order.setMarket("XSHG");
        order.setSecurityId("600030");
        order.setSide("B");
        order.setQty(100);
        order.setPrice(10.5);
        order.setShareholderId("SH12345678");
        return order;
    }

    @BeforeEach
    void setUp() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        Disruptor<OrderEvent> disruptor = mock(Disruptor.class);
        RingBuffer<OrderEvent> ringBuffer = mock(RingBuffer.class);
        when(disruptor.getRingBuffer()).thenReturn(ringBuffer);
        doNothing().when(ringBuffer).publishEvent(any());
        tradingService = new OrderService(orderRepository, disruptor);
    }

    @Test
    void validate_validOrder_passes() {
        OrderEntity order = validOrder("ORD0000000000001");
        ValidationResult validation = tradingService.validateOrder(Order.fromEntity(order));
        assertTrue(validation.isSuccess());
    }

    @Test
    void validate_nullOrder_fails() {
        ValidationResult validation = tradingService.validateOrder(null);
        assertFalse(validation.isSuccess());
        assertTrue(validation.getMessage().contains("order is null"));
    }

    @Test
    void validate_clOrderIdEmpty_fails() {
        OrderEntity order = validOrder("ORD0000000000002");
        order.setClOrderId("");
        ValidationResult validation = tradingService.validateOrder(Order.fromEntity(order));
        assertFalse(validation.isSuccess());
        assertTrue(validation.getMessage().contains("clOrderId"));
    }

    @Test
    void validate_marketInvalidValue_fails() {
        OrderEntity order = validOrder("ORD0000000000003");
        order.setMarket("ABCD");
        ValidationResult validation = tradingService.validateOrder(Order.fromEntity(order));
        assertFalse(validation.isSuccess());
        assertTrue(validation.getMessage().contains("market"));
    }

    @Test
    void validate_marketWrongLength_fails() {
        OrderEntity order = validOrder("ORD0000000000004");
        order.setMarket("XSHEE");
        ValidationResult validation = tradingService.validateOrder(Order.fromEntity(order));
        assertFalse(validation.isSuccess());
        assertTrue(validation.getMessage().contains("market"));
    }

    @Test
    void validate_sideInvalid_fails() {
        OrderEntity order = validOrder("ORD0000000000005");
        order.setSide("X");
        ValidationResult validation = tradingService.validateOrder(Order.fromEntity(order));
        assertFalse(validation.isSuccess());
        assertTrue(validation.getMessage().contains("side"));
    }

    @Test
    void validate_securityIdInvalidFormat_fails() {
        OrderEntity order = validOrder("ORD0000000000006");
        order.setSecurityId("60A030");
        ValidationResult validation = tradingService.validateOrder(Order.fromEntity(order));
        assertFalse(validation.isSuccess());
        assertTrue(validation.getMessage().contains("securityId"));
    }

    @Test
    void validate_qtyNegative_fails() {
        OrderEntity order = validOrder("ORD0000000000007");
        order.setQty(-1);
        ValidationResult validation = tradingService.validateOrder(Order.fromEntity(order));
        assertFalse(validation.isSuccess());
        assertTrue(validation.getMessage().contains("qty"));
    }

    @Test
    void validate_qtyZero_passes() {
        OrderEntity order = validOrder("ORD0000000000008");
        order.setQty(0);
        ValidationResult validation = tradingService.validateOrder(Order.fromEntity(order));
        assertTrue(validation.isSuccess());
    }

    @Test
    void validate_priceZero_fails() {
        OrderEntity order = validOrder("ORD0000000000009");
        order.setPrice(0.0);
        ValidationResult validation = tradingService.validateOrder(Order.fromEntity(order));
        assertFalse(validation.isSuccess());
        assertTrue(validation.getMessage().contains("price"));
    }

    @Test
    void validate_priceSmallPositive_passes() {
        OrderEntity order = validOrder("ORD0000000000010");
        order.setPrice(0.01);
        ValidationResult validation = tradingService.validateOrder(Order.fromEntity(order));
        assertTrue(validation.isSuccess());
    }

    @Test
    void validate_clOrderIdLengthBoundary_passes() {
        OrderEntity order = validOrder("ABCDEFGHIJKLMNOP");
        ValidationResult validation = tradingService.validateOrder(Order.fromEntity(order));
        assertTrue(validation.isSuccess());
    }

    @Test
    void validate_clOrderIdTooLong_fails() {
        OrderEntity order = validOrder("ABCDEFGHIJKLMNOPQ");
        ValidationResult validation = tradingService.validateOrder(Order.fromEntity(order));
        assertFalse(validation.isSuccess());
        assertTrue(validation.getMessage().contains("clOrderId"));
    }

    @Test
    void validate_shareholderIdBoundary_passes() {
        OrderEntity order = validOrder("ORD0000000000011");
        order.setShareholderId("SH1234567X");
        ValidationResult validation = tradingService.validateOrder(Order.fromEntity(order));
        assertTrue(validation.isSuccess());
    }

    @Test
    void validate_shareholderIdTooLong_fails() {
        OrderEntity order = validOrder("ORD0000000000012");
        order.setShareholderId("SH1234567XX");
        ValidationResult validation = tradingService.validateOrder(Order.fromEntity(order));
        assertFalse(validation.isSuccess());
        assertTrue(validation.getMessage().contains("shareholderId"));
    }
}
