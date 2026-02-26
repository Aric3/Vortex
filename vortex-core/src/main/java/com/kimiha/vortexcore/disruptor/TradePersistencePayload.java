package com.kimiha.vortexcore.disruptor;

import com.kimiha.vortexcore.engine.TradeResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TradePersistencePayload {
    private String execId;
    private LocalDateTime tradeTime;
    private String market;
    private TradeResult tradeResult;
    private String takerSide;
    private String takerShareholderId;
}
