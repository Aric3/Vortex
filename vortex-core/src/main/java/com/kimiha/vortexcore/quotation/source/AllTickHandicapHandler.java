package com.kimiha.vortexcore.quotation.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kimiha.vortexcore.model.QuoteLevel;
import com.kimiha.vortexcore.model.dto.alltick.AllTickHandicapDto;
import com.kimiha.vortexcore.model.dto.alltick.AllTickHandicapLevelDto;
import com.kimiha.vortexcore.quotation.cache.QuotationCache;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AllTick 买卖五档（盘口）订阅与解析：协议 22002 订阅、22999 推送，写入缓存 bids/asks。
 */
@Component
public class AllTickHandicapHandler {

    private static final int CMD_SUBSCRIBE_HANDICAP = 22002;
    /** 沪深 A 股盘口最大档数 */
    private static final int DEPTH_LEVEL = 5;

    private final QuotationCache quoteCache;
    private final ObjectMapper objectMapper;

    public AllTickHandicapHandler(QuotationCache quoteCache, ObjectMapper objectMapper) {
        this.quoteCache = quoteCache;
        this.objectMapper = objectMapper;
    }

    /** 构建订阅请求 JSON（cmd_id 22002） */
    public String buildSubscribeMessage(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            throw new IllegalArgumentException("symbols required for handicap subscribe");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\"cmd_id\":").append(CMD_SUBSCRIBE_HANDICAP)
                .append(",\"seq_id\":2,\"trace\":\"vortex-sub-handicap\",\"data\":{\"symbol_list\":[");
        for (int i = 0; i < symbols.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"code\":\"").append(symbols.get(i)).append("\",\"depth_level\":").append(DEPTH_LEVEL).append("}");
        }
        sb.append("]}}");
        return sb.toString();
    }

    /**
     * 处理 22999 推送，解析 bids/asks 后合并写入缓存。
     */
    public void handlePush(JsonNode data) {
        if (data == null) return;
        try {
            AllTickHandicapDto dto = objectMapper.treeToValue(data, AllTickHandicapDto.class);
            if (dto == null || dto.getCode() == null) return;
            long tickTimeMs = parseLong(dto.getTickTime(), 0L);
            if (tickTimeMs > 0 && tickTimeMs < 10_000_000_000L) {
                tickTimeMs *= 1000;
            }
            List<QuoteLevel> bids = toQuoteLevels(dto.getBids());
            List<QuoteLevel> asks = toQuoteLevels(dto.getAsks());
            quoteCache.putHandicap(dto.getCode(), bids, asks, tickTimeMs);
        } catch (Exception e) {
            // 热路径：仅静默跳过解析异常，不打印日志
        }
    }

    private static List<QuoteLevel> toQuoteLevels(List<AllTickHandicapLevelDto> levels) {
        if (levels == null || levels.isEmpty()) return Collections.emptyList();
        List<QuoteLevel> result = new ArrayList<>();
        for (AllTickHandicapLevelDto dto : levels) {
            if (dto == null) continue;
            double p = parseDouble(dto.getPrice(), Double.NaN);
            long v = parseLong(dto.getVolume(), 0L);
            if (!Double.isNaN(p)) {
                result.add(new QuoteLevel(p, v));
            }
        }
        return result;
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
