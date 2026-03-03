package com.kimiha.vortexcore.alltick;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * AllTick WebSocket 推送：最新成交（cmd_id 22998）的 data 部分。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AllTickTickDto {

    private String code;
    private String seq;
    @JsonProperty("tick_time")
    private String tickTime;
    private String price;
    private String volume;
    private String turnover;
    @JsonProperty("trade_direction")
    private Integer tradeDirection;
}
