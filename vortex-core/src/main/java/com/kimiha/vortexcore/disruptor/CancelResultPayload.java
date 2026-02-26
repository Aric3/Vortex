package com.kimiha.vortexcore.disruptor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 撤单结果：用于更新 cancellations 表或写 cancel_rejects
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelResultPayload {
    /** 撤单请求的 clOrderId，用于定位 cancellations 记录 */
    private String cancelRequestClOrderId;
    private String origClOrderId;
    private boolean success;
    private Integer canceledQty;
    private Integer cumQty;
    private Integer rejectCode;
    private String rejectText;
}
