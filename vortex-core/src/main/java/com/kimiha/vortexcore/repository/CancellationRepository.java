package com.kimiha.vortexcore.repository;

import com.kimiha.vortexcore.model.entity.CancellationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CancellationRepository extends JpaRepository<CancellationEntity, Long> {
    CancellationEntity findByClOrderId(String clOrderId);
}
