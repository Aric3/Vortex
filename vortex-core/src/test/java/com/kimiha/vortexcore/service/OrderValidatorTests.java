package com.kimiha.vortexcore.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kimiha.vortexcore.model.OrderEntity;
import org.junit.jupiter.api.Test;

class OrderValidatorTests {
    private final OrderValidator validator = new OrderValidator();

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

    @Test
    void validate_validOrder_passes() {
        OrderEntity order = validOrder("ORD0000000000001");
        assertDoesNotThrow(() -> validator.validate(order));
    }

    @Test
    void validate_nullOrder_fails() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(null));
    }

    @Test
    void validate_clOrderIdEmpty_fails() {
        OrderEntity order = validOrder("ORD0000000000002");
        order.setClOrderId("");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(order));
    }

    @Test
    void validate_marketInvalidValue_fails() {
        OrderEntity order = validOrder("ORD0000000000003");
        order.setMarket("ABCD");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(order));
    }

    @Test
    void validate_marketWrongLength_fails() {
        OrderEntity order = validOrder("ORD0000000000004");
        order.setMarket("XSHEE");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(order));
    }

    @Test
    void validate_sideInvalid_fails() {
        OrderEntity order = validOrder("ORD0000000000005");
        order.setSide("X");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(order));
    }

    @Test
    void validate_securityIdInvalidFormat_fails() {
        OrderEntity order = validOrder("ORD0000000000006");
        order.setSecurityId("60A030");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(order));
    }

    @Test
    void validate_qtyNegative_fails() {
        OrderEntity order = validOrder("ORD0000000000007");
        order.setQty(-1);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(order));
    }

    @Test
    void validate_qtyZero_passes() {
        OrderEntity order = validOrder("ORD0000000000008");
        order.setQty(0);
        assertDoesNotThrow(() -> validator.validate(order));
    }

    @Test
    void validate_priceZero_fails() {
        OrderEntity order = validOrder("ORD0000000000009");
        order.setPrice(0.0);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(order));
    }

    @Test
    void validate_priceSmallPositive_passes() {
        OrderEntity order = validOrder("ORD0000000000010");
        order.setPrice(0.01);
        assertDoesNotThrow(() -> validator.validate(order));
    }

    @Test
    void validate_clOrderIdLengthBoundary_passes() {
        OrderEntity order = validOrder("ABCDEFGHIJKLMNOP");
        assertDoesNotThrow(() -> validator.validate(order));
    }

    @Test
    void validate_clOrderIdTooLong_fails() {
        OrderEntity order = validOrder("ABCDEFGHIJKLMNOPQ");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(order));
    }

    @Test
    void validate_shareholderIdBoundary_passes() {
        OrderEntity order = validOrder("ORD0000000000011");
        order.setShareholderId("SH1234567X");
        assertDoesNotThrow(() -> validator.validate(order));
    }

    @Test
    void validate_shareholderIdTooLong_fails() {
        OrderEntity order = validOrder("ORD0000000000012");
        order.setShareholderId("SH1234567XX");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(order));
    }
}
