package com.kimiha.vortexcore.repository;

import com.kimiha.vortexcore.model.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    OrderEntity findByClOrderId(String clOrderId);
}
