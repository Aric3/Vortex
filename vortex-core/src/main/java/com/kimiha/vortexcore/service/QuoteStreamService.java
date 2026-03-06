package com.kimiha.vortexcore.service;

import com.kimiha.vortexcore.model.HandicapSnapshot;
import com.kimiha.vortexcore.model.TickSnapshot;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * 行情 SSE 推送：按 securityId 订阅，提供最新成交价（tick）与买卖五档（handicap）两个独立流。
 */
@Service
public class QuoteStreamService {

    private static final Duration PUSH_INTERVAL = Duration.ofMillis(500);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);

    private final QuotationService quotationService;

    public QuoteStreamService(QuotationService quotationService) {
        this.quotationService = quotationService;
    }

    /**
     * 订阅该标的最新成交价流：建立连接后立即推送当前 tick，之后按间隔周期推送。
     */
    public Flux<TickSnapshot> streamTick(String securityId) {
        if (securityId == null || securityId.isBlank()) {
            return Flux.error(new IllegalArgumentException("securityId required"));
        }
        Flux<TickSnapshot> initial = Flux.defer(() -> Flux.just(quotationService.getTick(securityId)));
        Flux<TickSnapshot> periodic = Flux.interval(PUSH_INTERVAL).map(t -> quotationService.getTick(securityId));
        Flux<TickSnapshot> heartbeat = Flux.interval(HEARTBEAT_INTERVAL).map(t -> quotationService.getTick(securityId));
        return Flux.concat(initial, Flux.merge(periodic, heartbeat));
    }

    /**
     * 订阅该标的买卖五档流：建立连接后立即推送当前 handicap，之后按间隔周期推送。
     */
    public Flux<HandicapSnapshot> streamHandicap(String securityId) {
        if (securityId == null || securityId.isBlank()) {
            return Flux.error(new IllegalArgumentException("securityId required"));
        }
        Flux<HandicapSnapshot> initial = Flux.defer(() -> Flux.just(quotationService.getHandicap(securityId)));
        Flux<HandicapSnapshot> periodic = Flux.interval(PUSH_INTERVAL).map(t -> quotationService.getHandicap(securityId));
        Flux<HandicapSnapshot> heartbeat = Flux.interval(HEARTBEAT_INTERVAL).map(t -> quotationService.getHandicap(securityId));
        return Flux.concat(initial, Flux.merge(periodic, heartbeat));
    }
}
