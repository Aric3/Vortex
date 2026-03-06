package com.kimiha.vortexcore.model.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "trades", uniqueConstraints = @UniqueConstraint(columnNames = "exec_id"))
@Data
public class TradeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String execId;
    private String securityId;
    private String market;
    private Double price;
    private int qty;

    @Column(name = "trade_time_epoch_ms")
    private Long tradeTimeEpochMs;

    private String takerClOrderId;
    private String makerClOrderId;
    private String takerSide;
    private String makerSide;
    private String takerShareholderId;
    private String makerShareholderId;
}
