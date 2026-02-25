package com.kimiha.vortexcore.model;

import lombok.Data;

@Data
public class ExchangeOrderDealAck {
    private String clOrderId;
    private String market;
    private String securityId;
    private String side;
    private Integer qty;
    private Double price;
    private String shareholderId;
    private String execId;
    private Integer execQty;
    private Double execPrice;
}
