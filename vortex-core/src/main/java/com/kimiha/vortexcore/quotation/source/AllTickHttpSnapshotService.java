package com.kimiha.vortexcore.quotation.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kimiha.vortexcore.config.QuotationProperties;
import com.kimiha.vortexcore.model.QuoteLevel;
import com.kimiha.vortexcore.model.dto.alltick.AllTickHandicapLevelDto;
import com.kimiha.vortexcore.model.dto.alltick.AllTickHttpHandicapSnapshot;
import com.kimiha.vortexcore.model.dto.alltick.AllTickHttpTickSnapshot;
import com.kimiha.vortexcore.quotation.cache.QuotationCache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AllTick HTTP 快照：启动时拉取 trade-tick（最新价）与 depth-tick（盘口），按 subscription-mode 决定拉取哪些。
 */
@Service
@ConditionalOnProperty(name = "quotation.enabled", havingValue = "true")
@ConditionalOnExpression("'${quotation.source:real_only}' == 'real_only' || '${quotation.source:real_only}' == 'real_then_simulated'")
public class AllTickHttpSnapshotService {

    private static final String STOCK_TRADE_TICK_URL = "https://quote.alltick.co/quote-stock-b-api/trade-tick";
    private static final String STOCK_DEPTH_TICK_URL = "https://quote.alltick.co/quote-stock-b-api/depth-tick";
    private static final Logger log = LoggerFactory.getLogger(AllTickHttpSnapshotService.class);

    private final QuotationProperties quotationProperties;
    private final QuotationCache quoteCache;
    private final ObjectMapper objectMapper;
    private final WebClient webClient = WebClient.builder().build();

    public AllTickHttpSnapshotService(QuotationProperties quotationProperties,
                                     QuotationCache quoteCache,
                                     ObjectMapper objectMapper) {
        this.quotationProperties = quotationProperties;
        this.quoteCache = quoteCache;
        this.objectMapper = objectMapper;
    }

    /**
     * 在应用启动时自动拉取 HTTP 快照，按 subscription-mode 拉取 tick 和/或 handicap
     */
    @EventListener(ApplicationReadyEvent.class)
    public void fetchSnapshotWhenReady() {
        String mode = subscriptionMode();
        if (isTickEnabled(mode)) fetchTickSnapshot();
        if (isHandicapEnabled(mode)) fetchHandicapSnapshot();
    }

    /** 拉取最新成交价（trade-tick） */
    public void fetchTickSnapshot() {
        var alltick = quotationProperties.getAlltick();
        if (alltick.getToken() == null || alltick.getToken().isBlank()
                || alltick.getSymbols() == null || alltick.getSymbols().isEmpty()) {
            return;
        }
        List<String> symbols = alltick.getSymbols();
        if (symbols.size() > 5) {
            symbols = symbols.subList(0, 5);
        }
        try {
            String queryJson = buildQuery(symbols);
            String url = STOCK_TRADE_TICK_URL
                    + "?token=" + URLEncoder.encode(alltick.getToken(), StandardCharsets.UTF_8)
                    + "&query=" + URLEncoder.encode(queryJson, StandardCharsets.UTF_8);

            String body = webClient.get().uri(URI.create(url))
                    .exchangeToMono(r -> {
                        if (r.statusCode().is2xxSuccessful()) {
                            return r.bodyToMono(String.class);
                        }
                        return r.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(b -> { throw new RuntimeException("HTTP " + r.statusCode() + " " + b); });
                    })
                    .block();
            if (body == null) return;

            AllTickHttpTickSnapshot resp = objectMapper.readValue(body, AllTickHttpTickSnapshot.class);
            if (resp == null || resp.getData() == null || resp.getData().getTickList() == null) return;

            for (AllTickHttpTickSnapshot.TickItem tick : resp.getData().getTickList()) {
                double price = parseDouble(tick.getPrice(), 0.0);
                long volume = parseLong(tick.getVolume(), 0L);
                long tickTimeMs = parseLong(tick.getTickTime(), 0L);
                if (tickTimeMs > 0 && tickTimeMs < 10_000_000_000L) {
                    tickTimeMs *= 1000;
                }
                quoteCache.putTick(tick.getCode(), price, volume, tickTimeMs);
                log.info("AllTick HTTP snapshot cached {} lastPrice={}", tick.getCode(), price);
            }
        } catch (Exception e) {
            log.warn("AllTick HTTP tick snapshot failed: {}", e.getMessage());
        }
    }

    /** 拉取买卖五档（depth-tick） */
    public void fetchHandicapSnapshot() {
        var alltick = quotationProperties.getAlltick();
        if (alltick.getToken() == null || alltick.getToken().isBlank()
                || alltick.getSymbols() == null || alltick.getSymbols().isEmpty()) {
            return;
        }
        List<String> symbols = alltick.getSymbols();
        if (symbols.size() > 5) symbols = symbols.subList(0, 5);
        try {
            String queryJson = buildQuery(symbols);
            String url = STOCK_DEPTH_TICK_URL
                    + "?token=" + URLEncoder.encode(alltick.getToken(), StandardCharsets.UTF_8)
                    + "&query=" + URLEncoder.encode(queryJson, StandardCharsets.UTF_8);

            String body = webClient.get().uri(URI.create(url))
                    .exchangeToMono(r -> {
                        if (r.statusCode().is2xxSuccessful()) return r.bodyToMono(String.class);
                        return r.bodyToMono(String.class).defaultIfEmpty("")
                                .map(b -> { throw new RuntimeException("HTTP " + r.statusCode() + " " + b); });
                    })
                    .block();
            if (body == null) return;

            AllTickHttpHandicapSnapshot resp = objectMapper.readValue(body, AllTickHttpHandicapSnapshot.class);
            if (resp == null || resp.getData() == null || resp.getData().getTickList() == null) return;

            for (AllTickHttpHandicapSnapshot.HandicapItem item : resp.getData().getTickList()) {
                long tickTimeMs = parseLong(item.getTickTime(), 0L);
                if (tickTimeMs > 0 && tickTimeMs < 10_000_000_000L) tickTimeMs *= 1000;
                List<QuoteLevel> bids = toQuoteLevels(item.getBids());
                List<QuoteLevel> asks = toQuoteLevels(item.getAsks());
                quoteCache.putHandicap(item.getCode(), bids, asks, tickTimeMs);
                log.info("AllTick HTTP handicap snapshot cached {} bids={} asks={}", item.getCode(), bids.size(), asks.size());
            }
        } catch (Exception e) {
            log.warn("AllTick HTTP handicap snapshot failed: {}", e.getMessage());
        }
    }

    private static List<QuoteLevel> toQuoteLevels(List<AllTickHandicapLevelDto> levels) {
        if (levels == null || levels.isEmpty()) return Collections.emptyList();
        List<QuoteLevel> result = new ArrayList<>();
        for (AllTickHandicapLevelDto dto : levels) {
            if (dto == null) continue;
            double p = parseDouble(dto.getPrice(), Double.NaN);
            long v = parseLong(dto.getVolume(), 0L);
            if (!Double.isNaN(p)) result.add(new QuoteLevel(p, v));
        }
        return result;
    }

    private String buildQuery(List<String> symbols) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"trace\":\"vortex-http-1\",\"data\":{\"symbol_list\":[");
        for (int i = 0; i < symbols.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"code\":\"").append(symbols.get(i)).append("\"}");
        }
        sb.append("]}}");
        return sb.toString();
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

    private String subscriptionMode() {
        String m = quotationProperties.getAlltick().getSubscriptionMode();
        return (m != null && !m.isBlank()) ? m.trim().toLowerCase() : "tick";
    }

    private boolean isTickEnabled(String mode) {
        return "tick".equals(mode) || "both".equals(mode);
    }

    private boolean isHandicapEnabled(String mode) {
        return "handicap".equals(mode) || "both".equals(mode);
    }
}
