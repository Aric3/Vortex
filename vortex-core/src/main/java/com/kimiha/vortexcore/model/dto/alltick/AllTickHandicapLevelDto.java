package com.kimiha.vortexcore.model.dto.alltick;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * AllTick 盘口单档：买一/卖一 等，价格与量均为字符串
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AllTickHandicapLevelDto {
    private String price;
    private String volume;
}
