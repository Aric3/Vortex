package com.kimiha.vortexcore.model.domain;

import com.kimiha.vortexcore.model.OrderStatus;
import com.kimiha.vortexcore.model.dto.OrderSubmitRequest;
import com.kimiha.vortexcore.model.entity.OrderEntity;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Builder;

/**
 * 领域订单：订单簿、撮合、Disruptor 内使用的可变订单对象；不做 JPA 持久化。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Order {

    private String clOrderId; // 客户端订单号 (16字节字符串)
    private String market; // 市场 (4字节字符串) XSHG: 上交所, XSHE: 深交所, BJSE: 北交所   
    private String securityId; // 股票代码 (6字节字符串)
    private String side; // 买卖方向 (1字节字符串) B: 买, S: 卖
    private int qty; // 下单数量 (4字节无符号整数)
    private Double price; // 订单价格 (8字节浮点数)
    private String shareholderId; // 股东号 (10字节字符串)
    private int orderQty; // 剩余待成交数量 (4字节无符号整数)
    private int cumQty; // 累计成交量 (4字节无符号整数)
    private OrderStatus status; // 订单状态 (New, PartiallyFilled, Filled, Canceled, Rejected)
    private long updatedTimeEpochMs; // 更新时间 (epoch 毫秒)
    private long createTimeEpochMs; // 创建时间 (epoch 毫秒)

    /** 从 OrderEntity 转为 Order（用于从 DB 加载后参与领域逻辑或返回前转 DTO 的中间表示） */
    public static Order fromEntity(OrderEntity e) {
        if (e == null) return null;
        return Order.builder()
        .clOrderId(e.getClOrderId())
        .market(e.getMarket())
        .securityId(e.getSecurityId())
        .side(e.getSide())
        .qty(e.getQty())
        .price(e.getPrice())
        .shareholderId(e.getShareholderId())
        .orderQty(e.getOrderQty())
        .cumQty(e.getCumQty())
        .status(e.getStatus())
        .updatedTimeEpochMs(e.getUpdatedTimeEpochMs() != null ? e.getUpdatedTimeEpochMs() : 0L)
        .createTimeEpochMs(e.getCreateTimeEpochMs() != null ? e.getCreateTimeEpochMs() : 0L)
        .build();
    }

    /** 将当前 Order 转为 OrderEntity（用于持久化 insert/update）；id 由持久化层分配。 */
    public OrderEntity toEntity() {
        return OrderEntity.builder()
        .clOrderId(clOrderId)
        .market(market)
        .securityId(securityId)
        .side(side)
        .qty(qty)
        .price(price)
        .shareholderId(shareholderId)
        .orderQty(orderQty)
        .cumQty(cumQty)
        .status(status)
        .updatedTimeEpochMs(updatedTimeEpochMs)
        .createTimeEpochMs(createTimeEpochMs)
        .build();
    }

    /** 从下单请求构建 Order（用于入队）；orderQty/cumQty/status 在 Handler 入簿前再设 */
    public static Order fromRequest(OrderSubmitRequest r) {
        if (r == null) return null;
        long nowMs = System.currentTimeMillis();
        return Order.builder()
        .clOrderId(r.clOrderId())
        .market(r.market())
        .securityId(r.securityId())
        .side(r.side())
        .qty(r.qty() != null ? r.qty() : 0)
        .price(r.price())
        .shareholderId(r.shareholderId())
        .orderQty(r.qty() != null ? r.qty() : 0)
        .cumQty(0)
        .status(OrderStatus.New)
        .updatedTimeEpochMs(nowMs)
        .createTimeEpochMs(nowMs)
        .build();
    }

    /** 复制当前状态（新对象），用于持久化快照，避免后续 executeMatch 修改影响。 */
    public static Order copySnapshot(Order source) {
        if (source == null) return null;
        return Order.builder()
        .clOrderId(source.getClOrderId())
        .market(source.getMarket())
        .securityId(source.getSecurityId())
        .side(source.getSide())
        .qty(source.getQty())
        .price(source.getPrice())
        .shareholderId(source.getShareholderId())
        .orderQty(source.getOrderQty())
        .cumQty(source.getCumQty())
        .status(source.getStatus())
        .updatedTimeEpochMs(source.getUpdatedTimeEpochMs())
        .createTimeEpochMs(source.getCreateTimeEpochMs())
        .build();
    }
}
