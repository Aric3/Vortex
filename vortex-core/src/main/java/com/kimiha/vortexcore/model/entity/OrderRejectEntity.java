package com.kimiha.vortexcore.model.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "order_rejects")
@Data
public class OrderRejectEntity {
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
    private Integer rejectCode;
    private String rejectText;
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }
}
