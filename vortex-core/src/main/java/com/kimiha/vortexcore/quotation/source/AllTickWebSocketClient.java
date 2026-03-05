package com.kimiha.vortexcore.quotation.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kimiha.vortexcore.config.QuotationProperties;
import com.kimiha.vortexcore.model.dto.alltick.AllTickWireMessage;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AllTick 行情 WebSocket 客户端：连接、按配置订阅最新成交价和/或买卖五档、心跳、断线重连。
 * 订阅类型由 quotation.alltick.subscription-mode 控制：tick | handicap | both。
 */
@Component
@ConditionalOnProperty(name = "quotation.enabled", havingValue = "true")
@ConditionalOnExpression("'${quotation.source:real_only}' != 'simulated_only'")
public class AllTickWebSocketClient {

    private static final int CMD_PUSH_TICK = 22998;
    private static final int CMD_PUSH_HANDICAP = 22999;
    private static final int CMD_HEARTBEAT = 22000;
    private static final Logger log = LoggerFactory.getLogger(AllTickWebSocketClient.class);

    private final QuotationProperties quotationProperties;
    private final AllTickTickHandler tickHandler;
    private final AllTickHandicapHandler handicapHandler;
    private final ObjectMapper objectMapper;
    private final WebSocketClient client = new ReactorNettyWebSocketClient();
    private final AtomicBoolean running = new AtomicBoolean(true);

    public AllTickWebSocketClient(QuotationProperties quotationProperties,
                                  AllTickTickHandler tickHandler,
                                  AllTickHandicapHandler handicapHandler,
                                  ObjectMapper objectMapper) {
        this.quotationProperties = quotationProperties;
        this.tickHandler = tickHandler;
        this.handicapHandler = handicapHandler;
        this.objectMapper = objectMapper;
    }

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

    @PreDestroy
    public void stop() {
        running.set(false);
    }

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

    private Mono<Void> handleSession(WebSocketSession session) {
        log.info("AllTick WebSocket connected");
        String mode = subscriptionMode();
        List<String> symbols = quotationProperties.getAlltick().getSymbols();

        List<Mono<WebSocketMessage>> subscribeMessages = new ArrayList<>();
        if (isTickEnabled(mode)) {
            subscribeMessages.add(Mono.just(session.textMessage(tickHandler.buildSubscribeMessage(symbols))));
        }
        if (isHandicapEnabled(mode)) {
            subscribeMessages.add(Mono.just(session.textMessage(handicapHandler.buildSubscribeMessage(symbols))));
        }
        if (subscribeMessages.isEmpty()) {
            log.warn("AllTick subscription-mode={} results in no subscription", mode);
        }

        String heartbeatJson = "{\"cmd_id\":" + CMD_HEARTBEAT + ",\"seq_id\":1,\"trace\":\"vortex-hb\",\"data\":{}}";
        Duration heartbeatDuration = Duration.ofMillis(quotationProperties.getAlltick().getHeartbeatIntervalMs());

        Flux<WebSocketMessage> send = Flux.concat(
                Flux.concat(subscribeMessages),
                Flux.interval(heartbeatDuration).map(__ -> session.textMessage(heartbeatJson))
        );

        Mono<Void> sendMono = session.send(send).then();
        Mono<Void> receiveMono = session.receive()
                .doOnNext(msg -> onMessage(msg.getPayloadAsText()))
                .then();
        return Mono.when(sendMono, receiveMono)
                .doOnTerminate(() -> log.info("AllTick WebSocket session ended"));
    }

    private void onMessage(String text) {
        try {
            AllTickWireMessage wire = objectMapper.readValue(text, AllTickWireMessage.class);
            if (wire == null || wire.getCmdId() == null || wire.getData() == null) return;
            String mode = subscriptionMode();
            if (wire.getCmdId() == CMD_PUSH_TICK && isTickEnabled(mode)) {
                tickHandler.handlePush(wire.getData());
            } else if (wire.getCmdId() == CMD_PUSH_HANDICAP && isHandicapEnabled(mode)) {
                handicapHandler.handlePush(wire.getData());
            }
        } catch (Exception e) {
            log.trace("AllTick message parse skip: {}", e.getMessage());
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
