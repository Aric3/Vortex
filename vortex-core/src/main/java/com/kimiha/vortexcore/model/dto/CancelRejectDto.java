package com.kimiha.vortexcore.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CancelRejectDto {
    public static final ReportType REPORT_TYPE = ReportType.CANCEL_REJECT;

    private String clOrderId; // 撤单请求的唯一编号 (16字节字符串)
    private String origClOrderId; // 已撤原始订单的唯一编号 (16字节字符串)
    private Integer rejectCode; // 错误代码 (4字节整数)
    private String rejectText; // 错误原因说明 (64字节字符串)
}
