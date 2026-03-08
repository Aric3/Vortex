package com.kimiha.vortexcore.model.dto;

/**
 * 单笔成交明细（与前端 ExecItem / 回报 OrderExecution 对齐）
 */
public record ExecutionDto(
        String execId,
        Integer execQty,
        Double execPrice
) {
}
