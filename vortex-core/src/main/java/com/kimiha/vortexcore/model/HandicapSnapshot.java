package com.kimiha.vortexcore.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 买卖五档快照（盘口）：仅含 bids/asks 与时间戳，来自 AllTick 22999
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HandicapSnapshot {
    /** AllTick 标的 code，如 600030.SH */
    private String code;
    /** 买盘深度，买一在前 */
    private List<QuoteLevel> bids;
    /** 卖盘深度，卖一在前 */
    private List<QuoteLevel> asks;
    /** 盘口时间戳（毫秒） */
    private long tickTimeMs;
}
