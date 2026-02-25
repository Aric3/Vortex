package com.kimiha.vortexcore.model;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "order_events")
@Data
public class OrderEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private OrderEventType eventType;

    private String clOrderId;
    private String origClOrderId;
    private String market;
    private String securityId;
    private String side;
    private Integer qty;
    private Double price;
    private String shareholderId;

    private Integer rejectCode;
    private String rejectText;

    private Integer cumQty;
    private Integer canceledQty;

    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
