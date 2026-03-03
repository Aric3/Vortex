package com.kimiha.vortexcore.alltick;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 启动时通过 AllTick HTTP 接口拉取最新价并写入缓存；当 alltick.http-refresh-interval-ms>0 时定时刷新。
 */
@Service
@ConditionalOnProperty(name = "alltick.enabled", havingValue = "true")
public class AllTickHttpSnapshotService {

    private static final String STOCK_TRADE_TICK_URL = "https://quote.alltick.co/quote-stock-b-api/trade-tick";
    private static final Logger log = LoggerFactory.getLogger(AllTickHttpSnapshotService.class);

    private final AllTickProperties properties;
    private final AllTickQuoteCache quoteCache;
    private final ObjectMapper objectMapper;
    private final WebClient webClient = WebClient.builder().build();

    private volatile long lastFetchTimeMs = 0;

    public AllTickHttpSnapshotService(AllTickProperties properties,
                                     AllTickQuoteCache quoteCache,
                                     ObjectMapper objectMapper) {
        this.properties = properties;
        this.quoteCache = quoteCache;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void fetchSnapshotWhenReady() {
        fetchSnapshot();
    }

    /** 当 alltick.http-refresh-interval-ms > 0 时，按间隔定时刷新（由 @Scheduled 每分钟检查一次）。 */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 60_000)
    public void scheduledRefreshIfEnabled() {
        if (properties.getHttpRefreshIntervalMs() <= 0) return;
        long now = System.currentTimeMillis();
        if (now - lastFetchTimeMs >= properties.getHttpRefreshIntervalMs()) {
            lastFetchTimeMs = now;
            fetchSnapshot();
        }
    }

    public void fetchSnapshot() {
        if (properties.getToken() == null || properties.getToken().isBlank()
                || properties.getSymbols() == null || properties.getSymbols().isEmpty()) {
            return;
        }
        List<String> symbols = properties.getSymbols();
        if (symbols.size() > 5) {
            symbols = symbols.subList(0, 5);
        }
        try {
            String queryJson = buildQuery(symbols);
            String url = STOCK_TRADE_TICK_URL
                    + "?token=" + URLEncoder.encode(properties.getToken(), StandardCharsets.UTF_8)
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

            AllTickHttpSnapshot resp = objectMapper.readValue(body, AllTickHttpSnapshot.class);
            if (resp == null || resp.getData() == null || resp.getData().getTickList() == null) return;

            for (AllTickHttpSnapshot.TickItem tick : resp.getData().getTickList()) {
                double price = parseDouble(tick.getPrice(), 0.0);
                long volume = parseLong(tick.getVolume(), 0L);
                long tickTimeMs = parseLong(tick.getTickTime(), 0L);
                if (tickTimeMs > 0 && tickTimeMs < 10_000_000_000L) {
                    tickTimeMs *= 1000;
                }
                QuoteSnapshot snapshot = new QuoteSnapshot(tick.getCode(), price, volume, tickTimeMs);
                quoteCache.put(tick.getCode(), snapshot);
                log.info("AllTick HTTP snapshot cached {} lastPrice={}", tick.getCode(), price);
            }
            lastFetchTimeMs = System.currentTimeMillis();
        } catch (Exception e) {
            log.warn("AllTick HTTP snapshot failed: {}", e.getMessage());
        }
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
}
