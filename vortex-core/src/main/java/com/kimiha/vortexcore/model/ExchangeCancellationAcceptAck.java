package com.kimiha.vortexcore.model;

import lombok.Data;

@Data
public class ExchangeCancellationAcceptAck {
    private String clOrderId;
    private String origClOrderId;
    private String market;
    private String securityId;
    private String side;
    private String shareholderId;
    private Integer qty;
    private Double price;
    private Integer cumQty;
    private Integer canceledQty;
}
