package com.kimiha.vortexcore.model.dto.alltick;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/** AllTick HTTP 盘口接口返回：/depth-tick */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AllTickHttpHandicapSnapshot {

    private Integer ret;
    private DataHolder data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataHolder {
        @JsonProperty("tick_list")
        private List<HandicapItem> tickList;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HandicapItem {
        private String code;
        private String seq;
        @JsonProperty("tick_time")
        private String tickTime;
        private List<AllTickHandicapLevelDto> bids;
        private List<AllTickHandicapLevelDto> asks;
    }
}
