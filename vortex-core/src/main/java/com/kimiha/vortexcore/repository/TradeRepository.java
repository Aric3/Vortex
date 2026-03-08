package com.kimiha.vortexcore.repository;

import com.kimiha.vortexcore.model.entity.TradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TradeRepository extends JpaRepository<TradeEntity, Long> {

    /** 按买方/卖方股东号查询其作为 taker 的成交，按时间正序（用于前端展示多笔成交明细） */
    java.util.List<TradeEntity> findByTakerShareholderIdOrderByTradeTimeEpochMsAsc(String takerShareholderId);
}
