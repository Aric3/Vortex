package com.kimiha.vortexcore.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 盘口单档：价格与数量，用于买卖五档。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuoteLevel {
    private double price;
    private long volume;
}
