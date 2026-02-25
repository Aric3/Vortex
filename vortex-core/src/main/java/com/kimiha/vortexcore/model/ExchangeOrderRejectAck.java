package com.kimiha.vortexcore.model;

import lombok.Data;

@Data
public class ExchangeOrderRejectAck {
    private String clOrderId;
    private String market;
    private String securityId;
    private String side;
    private Integer qty;
    private Double price;
    private String shareholderId;
    private Integer rejectCode;
    private String rejectText;
}
