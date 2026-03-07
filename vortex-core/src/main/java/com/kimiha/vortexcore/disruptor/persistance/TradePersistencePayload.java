package com.kimiha.vortexcore.disruptor.persistance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.kimiha.vortexcore.matching.TradeResult;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TradePersistencePayload {
    private String execId;
    private long tradeTimeEpochMs;
    private String market;
    private TradeResult tradeResult;
    private String takerSide;
    private String takerShareholderId;
}
