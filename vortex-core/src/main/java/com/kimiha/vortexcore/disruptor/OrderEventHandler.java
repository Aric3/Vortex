package com.kimiha.vortexcore.disruptor;

import com.kimiha.vortexcore.engine.MatchingEngine;
import com.kimiha.vortexcore.engine.OrderBook;
import com.kimiha.vortexcore.engine.OrderBookChangedEvent;
import com.kimiha.vortexcore.engine.TradeResult;
import com.kimiha.vortexcore.model.CancellationEntity;
import com.kimiha.vortexcore.model.OrderEntity;
import com.kimiha.vortexcore.model.ResultCode;
import com.kimiha.vortexcore.model.dto.CancelConfirmDto;
import com.kimiha.vortexcore.model.dto.CancelRejectDto;
import com.kimiha.vortexcore.model.dto.OrderConfirmDto;
import com.kimiha.vortexcore.model.dto.OrderExecutionDto;
import com.kimiha.vortexcore.model.dto.OrderRejectDto;
import com.kimiha.vortexcore.model.dto.OrderReportEnvelope;
import com.kimiha.vortexcore.service.OrderReportStreamService;
import com.kimiha.vortexcore.service.tools.ExecIdGenerator;
import com.lmax.disruptor.EventHandler;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderEventHandler implements EventHandler<OrderEvent> {

    private static final String REJECT_TEXT_WASH_TRADE = "Wash trade rejected";
    private static final String REJECT_TEXT_DUPLICATE_CL_ORDER_ID = "Duplicate clOrderId";
    private static final String REJECT_TEXT_CANCEL_ORDER_NOT_FOUND = "Order not found or already filled/canceled";

    private final MatchingEngine matchingEngine;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderReportStreamService reportStreamService;

    public OrderEventHandler(MatchingEngine matchingEngine, ApplicationEventPublisher eventPublisher,
                             OrderReportStreamService reportStreamService) {
        this.matchingEngine = matchingEngine;
        this.eventPublisher = eventPublisher; // 可为 null，测试时不推送
        this.reportStreamService = reportStreamService;
    }

    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        if (event.isCancel()) {
            handleCancel(event);
            return;
        }
        OrderEntity order = event.getOrder();
        String securityId = order.getSecurityId();
        OrderBook book = matchingEngine.getOrderBook(securityId);

        if (book.illegalClOrderId(order)) {
            if (reportStreamService != null) {
                OrderRejectDto rejectDto = OrderRejectDto.builder()
                        .clOrderId(order.getClOrderId())
                        .market(order.getMarket())
                        .securityId(securityId)
                        .side(order.getSide())
                        .qty(order.getQty())
                        .price(order.getPrice())
                        .shareholderId(order.getShareholderId())
                        .rejectCode(ResultCode.DUPLICATE_CL_ORDER_ID.getCode())
                        .rejectText(REJECT_TEXT_DUPLICATE_CL_ORDER_ID)
                        .build();
                reportStreamService.pushReport(order.getShareholderId(),
                        new OrderReportEnvelope(OrderRejectDto.REPORT_TYPE, rejectDto));
            }
            return;
        }

        if (book.isWashTrading(order)) {
            if (reportStreamService != null) {
                OrderRejectDto rejectDto = OrderRejectDto.builder()
                        .clOrderId(order.getClOrderId())
                        .market(order.getMarket())
                        .securityId(securityId)
                        .side(order.getSide())
                        .qty(order.getQty())
                        .price(order.getPrice())
                        .shareholderId(order.getShareholderId())
                        .rejectCode(ResultCode.WASH_TRADE_REJECT.getCode())
                        .rejectText(REJECT_TEXT_WASH_TRADE)
                        .build();
                reportStreamService.pushReport(order.getShareholderId(),
                        new OrderReportEnvelope(OrderRejectDto.REPORT_TYPE, rejectDto));
            }
            return;
        }

        if (reportStreamService != null) {
            OrderConfirmDto confirmDto = OrderConfirmDto.builder()
                    .clOrderId(order.getClOrderId())
                    .market(order.getMarket())
                    .securityId(securityId)
                    .side(order.getSide())
                    .qty(order.getQty())
                    .price(order.getPrice())
                    .shareholderId(order.getShareholderId())
                    .build();
            reportStreamService.pushReport(order.getShareholderId(),
                    new OrderReportEnvelope(OrderConfirmDto.REPORT_TYPE, confirmDto));
        }

        int takerOriginalQty = order.getQty();
        List<TradeResult> tradeResults = book.executeMatch(order);

        if (!tradeResults.isEmpty() && reportStreamService != null) {
            for (TradeResult tr : tradeResults) {
                String execId = ExecIdGenerator.next();
                // Taker 成交回报
                OrderExecutionDto takerReport = OrderExecutionDto.builder()
                        .clOrderId(tr.getTakerClOrderId())
                        .market(order.getMarket())
                        .securityId(securityId)
                        .side(order.getSide())
                        .qty(takerOriginalQty)
                        .price(order.getPrice())
                        .shareholderId(order.getShareholderId())
                        .execId(execId)
                        .execQty(tr.getQty())
                        .execPrice(tr.getPrice())
                        .build();
                reportStreamService.pushReport(order.getShareholderId(),
                        new OrderReportEnvelope(OrderExecutionDto.REPORT_TYPE, takerReport));
                // Maker 成交回报
                OrderExecutionDto makerReport = OrderExecutionDto.builder()
                        .clOrderId(tr.getMakerClOrderId())
                        .market(order.getMarket())
                        .securityId(securityId)
                        .side(tr.getMakerSide())
                        .qty(tr.getMakerOriginalQty())
                        .price(tr.getMakerPrice())
                        .shareholderId(tr.getMakerShareholderId())
                        .execId(execId)
                        .execQty(tr.getQty())
                        .execPrice(tr.getPrice())
                        .build();
                reportStreamService.pushReport(tr.getMakerShareholderId(),
                        new OrderReportEnvelope(OrderExecutionDto.REPORT_TYPE, makerReport));
            }
        }

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new OrderBookChangedEvent(this, securityId));
        }
    }

    /**
     * 处理撤单请求
     * @param event 撤单请求事件
     */
    private void handleCancel(OrderEvent event) {
        CancellationEntity cancellation = event.getCancellation();
        String origClOrderId = cancellation.getOrigClOrderId();
        String securityId = cancellation.getSecurityId();
        OrderBook book = matchingEngine.getOrderBook(securityId);
        // 从订单簿中移除原始订单
        OrderEntity removed = book.cancelByClOrderId(cancellation);

        if (reportStreamService != null) {
            if (removed != null) {
                CancelConfirmDto confirmDto = CancelConfirmDto.builder()
                        .clOrderId(origClOrderId)
                        .origClOrderId(origClOrderId)
                        .market(cancellation.getMarket())
                        .securityId(securityId)
                        .side(removed.getSide())
                        .shareholderId(removed.getShareholderId())
                        .qty(removed.getQty())
                        .price(removed.getPrice())
                        .cumQty(0)
                        .canceledQty(removed.getQty())
                        .build();
                reportStreamService.pushReport(cancellation.getShareholderId(),
                        new OrderReportEnvelope(CancelConfirmDto.REPORT_TYPE, confirmDto));
            } else {
                CancelRejectDto rejectDto = CancelRejectDto.builder()
                        .clOrderId(origClOrderId)
                        .origClOrderId(origClOrderId)
                        .rejectCode(ResultCode.NOT_FOUND.getCode())
                        .rejectText(REJECT_TEXT_CANCEL_ORDER_NOT_FOUND)
                        .build();
                reportStreamService.pushReport(cancellation.getShareholderId(),
                        new OrderReportEnvelope(CancelRejectDto.REPORT_TYPE, rejectDto));
            }
        }

        if (removed != null && eventPublisher != null) {
            eventPublisher.publishEvent(new OrderBookChangedEvent(this, securityId));
        }
    }
}