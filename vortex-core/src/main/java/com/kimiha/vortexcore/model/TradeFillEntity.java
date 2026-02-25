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
@Table(name = "trade_fills")
@Data
public class TradeFillEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String clOrderId;
    private String market;
    private String securityId;
    private String side;
    private Integer qty;
    private Double price;
    private String shareholderId;

    private String execId;
    private Integer execQty;
    private Double execPrice;

    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
