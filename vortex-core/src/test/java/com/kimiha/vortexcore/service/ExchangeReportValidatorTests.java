package com.kimiha.vortexcore.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kimiha.vortexcore.model.ExchangeCancellationAcceptAck;
import com.kimiha.vortexcore.model.ExchangeCancellationRejectAck;
import com.kimiha.vortexcore.model.ExchangeOrderAcceptAck;
import com.kimiha.vortexcore.model.ExchangeOrderDealAck;
import com.kimiha.vortexcore.model.ExchangeOrderRejectAck;
import org.junit.jupiter.api.Test;

class ExchangeReportValidatorTests {
    private final ExchangeReportValidator validator = new ExchangeReportValidator();

    private ExchangeOrderAcceptAck validOrderAccept() {
        ExchangeOrderAcceptAck ack = new ExchangeOrderAcceptAck();
        ack.setClOrderId("ORD0000000000001");
        ack.setMarket("XSHG");
        ack.setSecurityId("600030");
        ack.setSide("B");
        ack.setQty(100);
        ack.setPrice(10.5);
        ack.setShareholderId("SH12345678");
        return ack;
    }

    private ExchangeOrderRejectAck validOrderReject() {
        ExchangeOrderRejectAck ack = new ExchangeOrderRejectAck();
        ack.setClOrderId("ORD0000000000002");
        ack.setMarket("XSHE");
        ack.setSecurityId("000001");
        ack.setSide("S");
        ack.setQty(200);
        ack.setPrice(12.3);
        ack.setShareholderId("SZ12345678");
        ack.setRejectCode(100);
        ack.setRejectText("invalid");
        return ack;
    }

    private ExchangeOrderDealAck validOrderDeal() {
        ExchangeOrderDealAck ack = new ExchangeOrderDealAck();
        ack.setClOrderId("ORD0000000000003");
        ack.setMarket("BJSE");
        ack.setSecurityId("430001");
        ack.setSide("B");
        ack.setQty(300);
        ack.setPrice(8.88);
        ack.setShareholderId("BJ12345678");
        ack.setExecId("EXEC00000001");
        ack.setExecQty(100);
        ack.setExecPrice(8.9);
        return ack;
    }

    private ExchangeCancellationAcceptAck validCancelAccept() {
        ExchangeCancellationAcceptAck ack = new ExchangeCancellationAcceptAck();
        ack.setClOrderId("CAN0000000000001");
        ack.setOrigClOrderId("ORD0000000000004");
        ack.setMarket("XSHG");
        ack.setSecurityId("600031");
        ack.setSide("S");
        ack.setShareholderId("SH12345678");
        ack.setQty(400);
        ack.setPrice(11.0);
        ack.setCumQty(100);
        ack.setCanceledQty(300);
        return ack;
    }

    private ExchangeCancellationRejectAck validCancelReject() {
        ExchangeCancellationRejectAck ack = new ExchangeCancellationRejectAck();
        ack.setClOrderId("CAN0000000000002");
        ack.setOrigClOrderId("ORD0000000000005");
        ack.setRejectCode(200);
        ack.setRejectText("reject");
        return ack;
    }

    @Test
    void validate_orderAccept_valid() {
        assertDoesNotThrow(() -> validator.validateOrderAccept(validOrderAccept()));
    }

    @Test
    void validate_orderAccept_invalidClOrderId() {
        ExchangeOrderAcceptAck ack = validOrderAccept();
        ack.setClOrderId("SHORT");
        assertThrows(IllegalArgumentException.class, () -> validator.validateOrderAccept(ack));
    }

    @Test
    void validate_orderReject_valid() {
        assertDoesNotThrow(() -> validator.validateOrderReject(validOrderReject()));
    }

    @Test
    void validate_orderReject_missingRejectCode() {
        ExchangeOrderRejectAck ack = validOrderReject();
        ack.setRejectCode(null);
        assertThrows(IllegalArgumentException.class, () -> validator.validateOrderReject(ack));
    }

    @Test
    void validate_orderDeal_valid() {
        assertDoesNotThrow(() -> validator.validateOrderDeal(validOrderDeal()));
    }

    @Test
    void validate_orderDeal_invalidExecIdLength() {
        ExchangeOrderDealAck ack = validOrderDeal();
        ack.setExecId("EXEC1");
        assertThrows(IllegalArgumentException.class, () -> validator.validateOrderDeal(ack));
    }

    @Test
    void validate_cancelAccept_valid() {
        assertDoesNotThrow(() -> validator.validateCancellationAccept(validCancelAccept()));
    }

    @Test
    void validate_cancelAccept_missingOrigClOrderId() {
        ExchangeCancellationAcceptAck ack = validCancelAccept();
        ack.setOrigClOrderId(null);
        assertThrows(IllegalArgumentException.class, () -> validator.validateCancellationAccept(ack));
    }

    @Test
    void validate_cancelReject_valid() {
        assertDoesNotThrow(() -> validator.validateCancellationReject(validCancelReject()));
    }

    @Test
    void validate_cancelReject_invalidRejectText() {
        ExchangeCancellationRejectAck ack = validCancelReject();
        ack.setRejectText("");
        assertThrows(IllegalArgumentException.class, () -> validator.validateCancellationReject(ack));
    }
}
