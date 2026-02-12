package com.kimiha.vortexcore.model;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "orders")
@Data
public class OrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String clOrderId;// 客户端订单号 (16字节字符串)
    private String market;// 订单交易的市场 (4字节字符串) XSHG: 上交所, XSHE: 深交所, BJSE: 北交所
    private String securityId;// 订单交易的股票代码 (6字节字符串)
    private String side;// 订单买卖方向 (1字节字符串) B: 买, S: 卖
    private int qty;// 订单数量 (4字节无符号整数)
    private Double price;// 订单价格 (8字节浮点数)
    private String shareholderId;// 股东号 (10字节字符串)
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}