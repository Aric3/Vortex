package com.kimiha.vortexcore.model.dto.alltick;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * AllTick WebSocket 报文：含 cmd_id 与 data，用于解析推送
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AllTickWireMessage {

    @JsonProperty("cmd_id")
    private Integer cmdId;

    private AllTickTickDto data;
}
