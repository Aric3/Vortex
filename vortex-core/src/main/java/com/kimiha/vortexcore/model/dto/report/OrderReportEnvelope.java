package com.kimiha.vortexcore.model.dto.report;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 异步回报统一封装，便于客户端根据 reportType 区分订单成交、撤单确认等。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderReportEnvelope {
    /** 回报类型：EXECUTION=成交回报, CANCEL_CONFIRM=撤单确认, CANCEL_REJECT=撤单非法 */
    private ReportType reportType;
    private Object data;

}
