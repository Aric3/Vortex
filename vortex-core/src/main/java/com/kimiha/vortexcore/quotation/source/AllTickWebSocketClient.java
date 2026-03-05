package com.kimiha.vortexcore.quotation.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kimiha.vortexcore.config.QuotationProperties;
import com.kimiha.vortexcore.model.QuoteSnapshot;
import com.kimiha.vortexcore.model.dto.alltick.AllTickTickDto;
import com.kimiha.vortexcore.model.dto.alltick.AllTickWireMessage;
import com.kimiha.vortexcore.quotation.cache.QuotationCache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AllTick 行情 WebSocket 客户端：连接、订阅最新价、收推送写入 Caffeine，定时心跳，断线重连
 */
@Component
@ConditionalOnProperty(name = "quotation.enabled", havingValue = "true")
@ConditionalOnExpression("'${quotation.source:real_only}' != 'simulated_only'")
public class AllTickWebSocketClient {

    private static final int CMD_SUBSCRIBE_TICK = 22004;
    private static final int CMD_PUSH_TICK = 22998;
    private static final int CMD_HEARTBEAT = 22000;

    private static final Logger log = LoggerFactory.getLogger(AllTickWebSocketClient.class);

    private final QuotationProperties quotationProperties;
    private final QuotationCache quoteCache;
    private final ObjectMapper objectMapper;
    private final WebSocketClient client = new ReactorNettyWebSocketClient();

    private final AtomicBoolean running = new AtomicBoolean(true);

    public AllTickWebSocketClient(QuotationProperties quotationProperties,
                                  QuotationCache quoteCache,
                                  ObjectMapper objectMapper) {
        this.quotationProperties = quotationProperties;
        this.quoteCache = quoteCache;
        this.objectMapper = objectMapper;
    }

    /**
     * 在应用启动 创建 Bean 后自动启动 WebSocket 客户端
     */
    @PostConstruct
    public void start() {
        var alltick = quotationProperties.getAlltick();
        if (!quotationProperties.isEnabled() || alltick.getToken() == null || alltick.getToken().isBlank()) {
            log.info("AllTick disabled or token not set, skip WebSocket client");
            return;
        }
        if (alltick.getSymbols() == null || alltick.getSymbols().isEmpty()) {
            log.warn("AllTick enabled but no symbols configured");
            return;
        }
        runConnectLoop();
    }

    /**
     * 在应用关闭时停止 WebSocket 客户端
     */
    @PreDestroy
    public void stop() {
        running.set(false);
    }

    /**
     * 连接 AllTick WebSocket 服务器，并循环连接
     */
    private void runConnectLoop() {
        connect()
                .doOnError(e -> log.warn("AllTick WebSocket error, will reconnect: {}", e.getMessage()))
                .repeatWhen(attempts -> attempts
                        .takeWhile(__ -> running.get())
                        .delayElements(Duration.ofMillis(quotationProperties.getAlltick().getReconnectIntervalMs())))
                .subscribe(
                        __ -> {},
                        e -> log.error("AllTick client stopped with error", e),
                        () -> log.info("AllTick WebSocket client stopped")
                );
    }

    /**
     * 连接 AllTick WebSocket 服务器
     */
    private Mono<Void> connect() {
        var alltick = quotationProperties.getAlltick();
        String url = alltick.getQuoteUrl();
        if (!url.contains("?")) {
            url = url + "?token=" + alltick.getToken();
        } else {
            url = url + "&token=" + alltick.getToken();
        }
        URI uri = URI.create(url);

        return client.execute(uri, this::handleSession);
    }

    /**
     * 处理 WebSocket 会话
     */
    private Mono<Void> handleSession(WebSocketSession session) {
        log.info("AllTick WebSocket connected");

        String subscribeJson = buildSubscribeMessage();
        String heartbeatJson = buildHeartbeatMessage();
        Duration heartbeatDuration = Duration.ofMillis(quotationProperties.getAlltick().getHeartbeatIntervalMs());

        Flux<WebSocketMessage> send = Flux.concat(
                Mono.just(session.textMessage(subscribeJson)),
                Flux.interval(heartbeatDuration)
                        .map(__ -> session.textMessage(heartbeatJson))
        );

        Mono<Void> sendMono = session.send(send).then();
        Mono<Void> receiveMono = session.receive()
                .doOnNext(msg -> onMessage(msg.getPayloadAsText()))
                .then();

        return Mono.when(sendMono, receiveMono)
                .doOnTerminate(() -> log.info("AllTick WebSocket session ended"));
    }

    /**
     * 处理 WebSocket 消息
     */
    private void onMessage(String text) {
        try {
            AllTickWireMessage wire = objectMapper.readValue(text, AllTickWireMessage.class);
            if (wire == null || wire.getCmdId() == null) return;
            if (wire.getCmdId() == CMD_PUSH_TICK && wire.getData() != null) {
                AllTickTickDto dto = wire.getData();
                double price = parseDouble(dto.getPrice(), 0.0);
                long volume = parseLong(dto.getVolume(), 0L);
                long tickTimeMs = parseLong(dto.getTickTime(), 0L);
                if (tickTimeMs > 0 && tickTimeMs < 10_000_000_000L) {
                    tickTimeMs *= 1000;
                }
                QuoteSnapshot snapshot = new QuoteSnapshot(dto.getCode(), price, volume, tickTimeMs);
                quoteCache.put(dto.getCode(), snapshot);
                log.debug("AllTick cached quote {} lastPrice={} volume={}", dto.getCode(), price, volume);
            }
        } catch (Exception e) {
            log.trace("AllTick message parse skip: {}", e.getMessage());
        }
    }

    /**
     * 解析 Double 字符串
     */
    private static double parseDouble(String s, double def) {
        if (s == null || s.isBlank()) return def;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * 解析 Long 字符串
     */
    private static long parseLong(String s, long def) {
        if (s == null || s.isBlank()) return def;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * 构建订阅消息
     */
    private String buildSubscribeMessage() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"cmd_id\":").append(CMD_SUBSCRIBE_TICK)
                    .append(",\"seq_id\":1,\"trace\":\"vortex-sub\",\"data\":{\"symbol_list\":[");
            var symbols = quotationProperties.getAlltick().getSymbols();
            for (int i = 0; i < symbols.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("{\"code\":\"").append(symbols.get(i)).append("\"}");
            }
            sb.append("]}}");
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Build subscribe message failed", e);
        }
    }

    /**
     * 构建心跳消息
     */
    private String buildHeartbeatMessage() {
        return "{\"cmd_id\":" + CMD_HEARTBEAT + ",\"seq_id\":1,\"trace\":\"vortex-hb\",\"data\":{}}";
    }
}
