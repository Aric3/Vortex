package com.kimiha.vortexcore.repository;

import com.kimiha.vortexcore.model.entity.TradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TradeRepository extends JpaRepository<TradeEntity, Long> {
}
