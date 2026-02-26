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
@Table(name = "cancellations")
@Data
public class CancellationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    private String clOrderId; // 客户端订单号 (16字节字符串)
    private String origClOrderId; // 待撤原始订单的 clOrderId，下单时为空
    private String market; // 订单交易的市场 (4字节字符串) XSHG: 上交所, XSHE: 深交所, BJSE: 北交所
    private String securityId; // 订单交易的股票代码 (6字节字符串)
    private String side; // 订单买卖方向 (1字节字符串) B: 买, S: 卖
    private String shareholderId; // 股东号 (10字节字符串)

    private LocalDateTime createTime; // 创建时间

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
