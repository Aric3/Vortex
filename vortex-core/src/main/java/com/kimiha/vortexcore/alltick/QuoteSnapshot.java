package com.kimiha.vortexcore.alltick;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 缓存的行情快照：最新价、成交量、时间戳等，供撮合/风控读取。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuoteSnapshot {

    /** AllTick 标的 code，如 600030.SH */
    private String code;
    /** 最新成交价 */
    private double lastPrice;
    /** 最新成交量 */
    private long volume;
    /** 报价时间戳（毫秒） */
    private long tickTimeMs;
}
