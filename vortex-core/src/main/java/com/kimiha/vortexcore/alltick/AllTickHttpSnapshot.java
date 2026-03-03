package com.kimiha.vortexcore.alltick;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/** AllTick HTTP 最新价接口返回：/trade-tick */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AllTickHttpSnapshot {

    private Integer ret;
    private DataHolder data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataHolder {
        @JsonProperty("tick_list")
        private List<TickItem> tickList;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TickItem {
        private String code;
        private String price;
        private String volume;
        @JsonProperty("tick_time")
        private String tickTime;
    }
}
