package com.kimiha.vortexcore.quotation.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kimiha.vortexcore.model.dto.alltick.AllTickTickDto;
import com.kimiha.vortexcore.quotation.cache.QuotationCache;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AllTick 最新成交价（逐笔）订阅与解析：协议 22004 订阅、22998 推送，写入缓存仅含 lastPrice/volume。
 */
@Component
public class AllTickTickHandler {

    private static final int CMD_SUBSCRIBE_TICK = 22004;

    private final QuotationCache quoteCache;
    private final ObjectMapper objectMapper;

    public AllTickTickHandler(QuotationCache quoteCache, ObjectMapper objectMapper) {
        this.quoteCache = quoteCache;
        this.objectMapper = objectMapper;
    }

    /** 构建订阅请求 JSON（cmd_id 22004） */
    public String buildSubscribeMessage(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            throw new IllegalArgumentException("symbols required for tick subscribe");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\"cmd_id\":").append(CMD_SUBSCRIBE_TICK)
                .append(",\"seq_id\":1,\"trace\":\"vortex-sub-tick\",\"data\":{\"symbol_list\":[");
        for (int i = 0; i < symbols.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"code\":\"").append(symbols.get(i)).append("\"}");
        }
        sb.append("]}}");
        return sb.toString();
    }

    /**
     * 处理 22998 推送，解析后写入缓存（仅更新 lastPrice/volume/tickTimeMs，不包含五档）。
     */
    public void handlePush(JsonNode data) {
        if (data == null) return;
        try {
            AllTickTickDto dto = objectMapper.treeToValue(data, AllTickTickDto.class);
            if (dto == null) return;
            double price = parseDouble(dto.getPrice(), 0.0);
            long volume = parseLong(dto.getVolume(), 0L);
            long tickTimeMs = parseLong(dto.getTickTime(), 0L);
            if (tickTimeMs > 0 && tickTimeMs < 10_000_000_000L) {
                tickTimeMs *= 1000;
            }
            quoteCache.putTick(dto.getCode(), price, volume, tickTimeMs);
        } catch (Exception e) {
            // 热路径：仅静默跳过解析异常，不打印日志
        }
    }

    private static double parseDouble(String s, double def) {
        if (s == null || s.isBlank()) return def;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static long parseLong(String s, long def) {
        if (s == null || s.isBlank()) return def;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
