package com.kimiha.vortexcore.repository;

import com.kimiha.vortexcore.model.entity.CancelRejectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CancelRejectRepository extends JpaRepository<CancelRejectEntity, Long> {
}
