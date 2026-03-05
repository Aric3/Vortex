package com.kimiha.vortexcore.model.dto.alltick;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * AllTick 盘口推送（cmd_id 22999）的 data 部分：买卖深度
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AllTickHandicapDto {
    /** AllTick 股票代码，如 600030.SH、HK-1288 */
    private String code;
    private String seq;
    /** 报价时间戳，单位毫秒（或秒，需与 22998 一致判断） */
    @JsonProperty("tick_time")
    private String tickTime;
    /** 买盘深度，买一在前 */
    private List<AllTickHandicapLevelDto> bids;
    /** 卖盘深度，卖一在前 */
    private List<AllTickHandicapLevelDto> asks;
}
