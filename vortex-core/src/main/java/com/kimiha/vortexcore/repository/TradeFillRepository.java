package com.kimiha.vortexcore.repository;

import com.kimiha.vortexcore.model.TradeFillEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TradeFillRepository extends JpaRepository<TradeFillEntity, Long> {}
