package com.kimiha.vortexcore.service;

import com.kimiha.vortexcore.disruptor.CancelResultPayload;
import com.kimiha.vortexcore.disruptor.PersistenceEvent;
import com.kimiha.vortexcore.disruptor.TradePersistencePayload;
import com.kimiha.vortexcore.model.entity.*;
import com.kimiha.vortexcore.model.domain.Order;
import com.kimiha.vortexcore.model.dto.OrderReject;
import com.kimiha.vortexcore.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PersistenceService {

    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final OrderRejectRepository orderRejectRepository;
    private final CancellationRepository cancellationRepository;
    private final CancelRejectRepository cancelRejectRepository;

    public PersistenceService(OrderRepository orderRepository,
                              TradeRepository tradeRepository,
                              OrderRejectRepository orderRejectRepository,
                              CancellationRepository cancellationRepository,
                              CancelRejectRepository cancelRejectRepository) {
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
        this.orderRejectRepository = orderRejectRepository;
        this.cancellationRepository = cancellationRepository;
        this.cancelRejectRepository = cancelRejectRepository;
    }

    @Transactional(rollbackFor = Exception.class)
    public void processBatch(List<PersistenceEvent> events) {
        if (events == null || events.isEmpty()) return;

        List<OrderEntity> ordersToInsert = new ArrayList<>();
        List<Order> ordersToUpdate = new ArrayList<>();
        List<TradeEntity> tradesToInsert = new ArrayList<>();
        List<OrderRejectEntity> orderRejectsToInsert = new ArrayList<>();
        List<CancellationEntity> cancellationsToInsert = new ArrayList<>();
        List<CancelResultPayload> cancelConfirmed = new ArrayList<>();
        List<CancelResultPayload> cancelRejected = new ArrayList<>();

        for (PersistenceEvent event : events) {
            if (event == null || event.getType() == null || event.getPayload() == null) continue;
            switch (event.getType()) {
                case ORDER_ACCEPTED -> {
                    Order o = (Order) event.getPayload();
                    ordersToInsert.add(o.toEntity());
                }
                case ORDER_UPDATED -> ordersToUpdate.add((Order) event.getPayload());
                case TRADE -> {
                    TradePersistencePayload p = (TradePersistencePayload) event.getPayload();
                    tradesToInsert.add(toTradeEntity(p));
                }
                case ORDER_REJECT -> orderRejectsToInsert.add(toOrderRejectEntity((OrderReject) event.getPayload()));
                case CANCEL_REQUEST -> cancellationsToInsert.add((CancellationEntity) event.getPayload());
                case CANCEL_CONFIRMED -> cancelConfirmed.add((CancelResultPayload) event.getPayload());
                case CANCEL_REJECTED -> cancelRejected.add((CancelResultPayload) event.getPayload());
            }
        }

        // FIXME:ORDER_ACCEPTED 幂等：同一 cl_order_id 可能因重试/重复事件已存在，只插入不存在的订单，避免整批回滚导致 TRADE 等未落库
        for (OrderEntity e : ordersToInsert) {
            if (orderRepository.findByClOrderId(e.getClOrderId()) == null) {
                orderRepository.save(e);
            }
        }
        // FIXME:ORDER_UPDATED 幂等：findByClOrderId 可能返回多个订单（真实交易情况下不会出现）
        // 状态更新只会涉及NEW/PARTIALLY_FILLED/FILLED/CANCELED 的合法流转，其他状态不会更新
        for (Order order : ordersToUpdate) {
            OrderEntity existing = orderRepository.findByClOrderId(order.getClOrderId());
            if (existing != null) {
                existing.setStatus(order.getStatus());
                existing.setCumQty(order.getCumQty());
                existing.setUpdatedTime(order.getUpdatedTime() != null ? order.getUpdatedTime() : LocalDateTime.now());
                orderRepository.save(existing);
            }
        }

        tradeRepository.saveAll(tradesToInsert);
        orderRejectRepository.saveAll(orderRejectsToInsert);
        cancellationRepository.saveAll(cancellationsToInsert);

        for (CancelResultPayload p : cancelConfirmed) {
            CancellationEntity c = cancellationRepository.findByClOrderId(p.getCancelRequestClOrderId());
            if (c != null) {
                c.setSuccess(true);
                c.setCanceledQty(p.getCanceledQty());
                c.setCumQty(p.getCumQty());
                c.setUpdatedTime(LocalDateTime.now());
                cancellationRepository.save(c);
            }
        }

        for (CancelResultPayload p : cancelRejected) {
            CancellationEntity c = cancellationRepository.findByClOrderId(p.getCancelRequestClOrderId());
            if (c != null) {
                c.setSuccess(false);
                c.setRejectCode(p.getRejectCode());
                c.setRejectText(p.getRejectText());
                c.setUpdatedTime(LocalDateTime.now());
                cancellationRepository.save(c);
            }
            CancelRejectEntity rej = new CancelRejectEntity();
            rej.setClOrderId(p.getCancelRequestClOrderId());
            rej.setOrigClOrderId(p.getOrigClOrderId());
            rej.setRejectCode(p.getRejectCode());
            rej.setRejectText(p.getRejectText());
            cancelRejectRepository.save(rej);
        }
    }

    private static TradeEntity toTradeEntity(TradePersistencePayload p) {
        var tr = p.getTradeResult();
        TradeEntity e = new TradeEntity();
        e.setExecId(p.getExecId());
        e.setTradeTime(p.getTradeTime() != null ? p.getTradeTime() : LocalDateTime.now());
        e.setMarket(p.getMarket());
        e.setSecurityId(tr.securityId());
        e.setPrice(tr.price());
        e.setQty(tr.qty());
        e.setTakerClOrderId(tr.takerClOrderId());
        e.setMakerClOrderId(tr.makerClOrderId());
        e.setTakerSide(p.getTakerSide());
        e.setMakerSide(tr.makerSide());
        e.setTakerShareholderId(p.getTakerShareholderId());
        e.setMakerShareholderId(tr.makerShareholderId());
        return e;
    }

    private static OrderRejectEntity toOrderRejectEntity(OrderReject r) {
        OrderRejectEntity e = new OrderRejectEntity();
        e.setClOrderId(r.clOrderId());
        e.setMarket(r.market());
        e.setSecurityId(r.securityId());
        e.setSide(r.side());
        e.setQty(r.qty());
        e.setPrice(r.price());
        e.setShareholderId(r.shareholderId());
        e.setRejectCode(r.rejectCode());
        e.setRejectText(r.rejectText());
        return e;
    }
}
