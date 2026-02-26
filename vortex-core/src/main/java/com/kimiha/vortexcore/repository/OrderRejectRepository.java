package com.kimiha.vortexcore.repository;

import com.kimiha.vortexcore.model.entity.OrderRejectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRejectRepository extends JpaRepository<OrderRejectEntity, Long> {
}
