package com.kimiha.vortexcore.model.dto;

import java.util.List;

public record OrderHistoryPageResponse(
        List<OrderResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
}
