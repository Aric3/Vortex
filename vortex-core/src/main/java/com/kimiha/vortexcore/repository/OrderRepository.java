package com.kimiha.vortexcore.repository;

import com.kimiha.vortexcore.model.entity.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    OrderEntity findByClOrderId(String clOrderId);
    Page<OrderEntity> findByShareholderId(String shareholderId, Pageable pageable);
}
