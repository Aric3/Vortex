package com.kimiha.vortexcore.model.dto;

public enum ReportType {
    ORDER_CONFIRM("ORDER_CONFIRM"),
    ORDER_REJECT("ORDER_REJECT"),
    ORDER_EXECUTION("ORDER_EXECUTION"),
    CANCEL_CONFIRM("CANCEL_CONFIRM"),
    CANCEL_REJECT("CANCEL_REJECT");

    private final String value;

    ReportType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
