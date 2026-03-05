package com.kimiha.vortexcore.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 最新成交价快照（逐笔）：仅含最新价、成交量、时间戳，来自 AllTick 22998 或模拟引擎
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TickSnapshot {
    /** AllTick 标的 code，如 600030.SH */
    private String code;
    /** 最新成交价 */
    private double lastPrice;
    /** 最新成交量 */
    private long volume;
    /** 报价时间戳（毫秒） */
    private long tickTimeMs;
}
