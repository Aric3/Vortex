package com.kimiha.vortexcore.model.entity;

import com.kimiha.vortexcore.model.OrderStatus;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Builder;

@Entity
@Table(name = "orders")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String clOrderId;
    private String market;
    private String securityId;
    private String side;
    private int qty;
    private Double price;
    private String shareholderId;

    @Column(columnDefinition = "integer not null default 0")
    private int orderQty;
    @Column(columnDefinition = "integer not null default 0")
    private int cumQty;
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(255) check (status in ('New','PartiallyFilled','Filled','Canceled','Rejected')) default 'New'")
    private OrderStatus status;
    private LocalDateTime updatedTime;
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        if (updatedTime == null) updatedTime = createTime;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedTime = LocalDateTime.now();
    }

    public static OrderEntity copySnapshot(OrderEntity source) {
        OrderEntity copy = new OrderEntity();
        copy.setClOrderId(source.getClOrderId());
        copy.setMarket(source.getMarket());
        copy.setSecurityId(source.getSecurityId());
        copy.setSide(source.getSide());
        copy.setQty(source.getQty());
        copy.setPrice(source.getPrice());
        copy.setShareholderId(source.getShareholderId());
        copy.setOrderQty(source.getOrderQty());
        copy.setCumQty(source.getCumQty());
        copy.setStatus(source.getStatus());
        copy.setUpdatedTime(source.getUpdatedTime());
        copy.setCreateTime(source.getCreateTime());
        return copy;
    }
}
