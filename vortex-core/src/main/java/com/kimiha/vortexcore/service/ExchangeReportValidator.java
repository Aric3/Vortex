package com.kimiha.vortexcore.service;

import com.kimiha.vortexcore.model.ExchangeCancellationAcceptAck;
import com.kimiha.vortexcore.model.ExchangeCancellationRejectAck;
import com.kimiha.vortexcore.model.ExchangeOrderAcceptAck;
import com.kimiha.vortexcore.model.ExchangeOrderDealAck;
import com.kimiha.vortexcore.model.ExchangeOrderRejectAck;

import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class ExchangeReportValidator {
    private static final Set<String> VALID_MARKETS = Set.of("XSHG", "XSHE", "BJSE");
    private static final Set<String> VALID_SIDES = Set.of("B", "S");

    public void validateOrderAccept(ExchangeOrderAcceptAck ack) {
        requireNotNull(ack, "ack");
        validateOrderBase(
                ack.getClOrderId(),
                ack.getMarket(),
                ack.getSecurityId(),
                ack.getSide(),
                ack.getQty(),
                ack.getPrice(),
                ack.getShareholderId());
    }

    public void validateOrderReject(ExchangeOrderRejectAck ack) {
        requireNotNull(ack, "ack");
        validateOrderBase(
                ack.getClOrderId(),
                ack.getMarket(),
                ack.getSecurityId(),
                ack.getSide(),
                ack.getQty(),
                ack.getPrice(),
                ack.getShareholderId());
        validateRejectFields(ack.getRejectCode(), ack.getRejectText());
    }

    public void validateOrderDeal(ExchangeOrderDealAck ack) {
        requireNotNull(ack, "ack");
        validateOrderBase(
                ack.getClOrderId(),
                ack.getMarket(),
                ack.getSecurityId(),
                ack.getSide(),
                ack.getQty(),
                ack.getPrice(),
                ack.getShareholderId());
        if (ack.getExecId() == null || ack.getExecId().length() != 12) {
            throw new IllegalArgumentException("execId invalid");
        }
        if (ack.getExecQty() == null || ack.getExecQty() < 0) {
            throw new IllegalArgumentException("execQty invalid");
        }
        if (ack.getExecPrice() == null || ack.getExecPrice() <= 0) {
            throw new IllegalArgumentException("execPrice invalid");
        }
    }

    public void validateCancellationAccept(ExchangeCancellationAcceptAck ack) {
        requireNotNull(ack, "ack");
        validateOrderBase(
                ack.getClOrderId(),
                ack.getMarket(),
                ack.getSecurityId(),
                ack.getSide(),
                ack.getQty(),
                ack.getPrice(),
                ack.getShareholderId());
        validateOrigClOrderId(ack.getOrigClOrderId());
        if (ack.getCumQty() == null || ack.getCumQty() < 0) {
            throw new IllegalArgumentException("cumQty invalid");
        }
        if (ack.getCanceledQty() == null || ack.getCanceledQty() < 0) {
            throw new IllegalArgumentException("canceledQty invalid");
        }
    }

    public void validateCancellationReject(ExchangeCancellationRejectAck ack) {
        requireNotNull(ack, "ack");
        validateClOrderId(ack.getClOrderId());
        validateOrigClOrderId(ack.getOrigClOrderId());
        validateRejectFields(ack.getRejectCode(), ack.getRejectText());
    }

    private void validateOrderBase(
            String clOrderId,
            String market,
            String securityId,
            String side,
            Integer qty,
            Double price,
            String shareholderId) {
        validateClOrderId(clOrderId);
        if (market == null || market.length() != 4 || !VALID_MARKETS.contains(market)) {
            throw new IllegalArgumentException("market invalid");
        }
        if (securityId == null || !securityId.matches("\\d{6}")) {
            throw new IllegalArgumentException("securityId invalid");
        }
        if (side == null || side.length() != 1 || !VALID_SIDES.contains(side)) {
            throw new IllegalArgumentException("side invalid");
        }
        if (qty == null || qty < 0) {
            throw new IllegalArgumentException("qty invalid");
        }
        if (price == null || price <= 0) {
            throw new IllegalArgumentException("price invalid");
        }
        if (shareholderId == null || shareholderId.length() != 10) {
            throw new IllegalArgumentException("shareholderId invalid");
        }
    }

    private void validateClOrderId(String clOrderId) {
        if (clOrderId == null || clOrderId.length() != 16) {
            throw new IllegalArgumentException("clOrderId invalid");
        }
    }

    private void validateOrigClOrderId(String origClOrderId) {
        if (origClOrderId == null || origClOrderId.length() != 16) {
            throw new IllegalArgumentException("origClOrderId invalid");
        }
    }

    private void validateRejectFields(Integer rejectCode, String rejectText) {
        if (rejectCode == null) {
            throw new IllegalArgumentException("rejectCode invalid");
        }
        if (rejectText == null || rejectText.isEmpty() || rejectText.length() > 64) {
            throw new IllegalArgumentException("rejectText invalid");
        }
    }

    private void requireNotNull(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is null");
        }
    }
}
