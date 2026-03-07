package com.kimiha.vortexcore.model.dto.alltick;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * AllTick WebSocket 推送：最新成交（cmd_id 22998）的 data 部分。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AllTickTickDto {

    /** AllTick 股票代码，格式security_id + .SH / .SZ / .BJ / .HK / .US，如 600030.SH */
    private String code;
    /** 序列号 */
    private String seq;
    /** 成交时间 */
    @JsonProperty("tick_time")
    private String tickTime;
    /** 成交价格 */
    private String price;
    /** 成交量 */
    private String volume;
    /** 成交额 */
    private String turnover;
    /** 成交方向 */
    @JsonProperty("trade_direction")
    private Integer tradeDirection;
}
