package com.kimiha.vortexcore.model;

import lombok.Data;

@Data
public class ExchangeCancellationRejectAck {
    private String clOrderId;
    private String origClOrderId;
    private Integer rejectCode;
    private String rejectText;
}
