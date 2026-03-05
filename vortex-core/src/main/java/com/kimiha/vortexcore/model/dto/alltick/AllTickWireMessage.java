package com.kimiha.vortexcore.model.dto.alltick;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * AllTick WebSocket 报文：含 cmd_id 与 data，用于解析推送。
 * data 为 JsonNode，按 cmd_id 分别解析为 AllTickTickDto（22998）或 AllTickHandicapDto（22999）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AllTickWireMessage {

    @JsonProperty("cmd_id")
    private Integer cmdId;

    private JsonNode data;
}
