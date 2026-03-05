package com.kimiha.vortexcore.quotation.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kimiha.vortexcore.config.QuotationProperties;
import com.kimiha.vortexcore.model.QuoteSnapshot;
import com.kimiha.vortexcore.model.dto.alltick.AllTickHttpSnapshot;
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
import java.util.List;

@Service
@ConditionalOnProperty(name = "quotation.enabled", havingValue = "true")
@ConditionalOnExpression("'${quotation.source:real_only}' == 'real_only' || '${quotation.source:real_only}' == 'real_then_simulated'")
public class AllTickHttpSnapshotService {

    private static final String STOCK_TRADE_TICK_URL = "https://quote.alltick.co/quote-stock-b-api/trade-tick";
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
     * 在应用启动时自动拉取一次 HTTP 快照，缓存到 Caffeine 中
     */
    @EventListener(ApplicationReadyEvent.class)
    public void fetchSnapshotWhenReady() {
        fetchSnapshot();
    }

    public void fetchSnapshot() {
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
