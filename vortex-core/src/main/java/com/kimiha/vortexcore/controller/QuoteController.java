package com.kimiha.vortexcore.controller;

import com.kimiha.vortexcore.config.QuotationProperties;
import com.kimiha.vortexcore.model.HandicapSnapshot;
import com.kimiha.vortexcore.model.Result;
import com.kimiha.vortexcore.model.TickSnapshot;
import com.kimiha.vortexcore.service.QuotationService;
import com.kimiha.vortexcore.service.QuoteStreamService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 行情查询：tick 与 handicap 分两个接口返回；另提供 SSE 推送供前端参考价。
 */
@RestController
@RequestMapping("/api/v1/vclient")
public class QuoteController {

    private final QuotationService quotationService;
    private final QuoteStreamService quoteStreamService;
    private final QuotationProperties quotationProperties;

    public QuoteController(QuotationService quotationService,
                           QuoteStreamService quoteStreamService,
                           QuotationProperties quotationProperties) {
        this.quotationService = quotationService;
        this.quoteStreamService = quoteStreamService;
        this.quotationProperties = quotationProperties;
    }

    /**
     * 可选股票代码列表（来自配置 symbols）。
     * GET /api/v1/vclient/quote/symbols
     */
    @GetMapping("/quote/symbols")
    public Result getSymbols() {
        List<String> raw = new ArrayList<>();
        if (quotationProperties.getSimulation() != null && quotationProperties.getSimulation().getSymbols() != null) {
            raw.addAll(quotationProperties.getSimulation().getSymbols());
        }
        if (quotationProperties.getAlltick() != null && quotationProperties.getAlltick().getSymbols() != null) {
            raw.addAll(quotationProperties.getAlltick().getSymbols());
        }

        List<String> symbols = raw.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    int idx = s.indexOf('.');
                    return idx > 0 ? s.substring(0, idx) : s;
                })
                .distinct()
                .toList();

        return Result.success(symbols, "OK");
    }

    /**
     * 最新成交价（逐笔）
     * GET /api/v1/vclient/quote/tick/{securityId}
     */
    @GetMapping("/quote/tick/{securityId}")
    public Result getTick(@PathVariable String securityId) {
        TickSnapshot tick = quotationService.getTick(securityId);
        return Result.success(tick);
    }

    /**
     * 买卖五档（盘口）
     * GET /api/v1/vclient/quote/handicap/{securityId}
     */
    @GetMapping("/quote/handicap/{securityId}")
    public Result getHandicap(@PathVariable String securityId) {
        HandicapSnapshot handicap = quotationService.getHandicap(securityId);
        return Result.success(handicap);
    }

    /**
     * 行情 SSE 推送（最新成交价）：连接后持续推送 tick，供前端参考价。
     * GET /api/v1/vclient/quote/stream/tick/{securityId}
     */
    @GetMapping(value = "/quote/stream/tick/{securityId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<TickSnapshot> streamTick(@PathVariable String securityId) {
        return quoteStreamService.streamTick(securityId);
    }

    /**
     * 行情 SSE 推送（买卖五档）：连接后持续推送 handicap，供前端盘口展示。
     * GET /api/v1/vclient/quote/stream/handicap/{securityId}
     */
    @GetMapping(value = "/quote/stream/handicap/{securityId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<HandicapSnapshot> streamHandicap(@PathVariable String securityId) {
        return quoteStreamService.streamHandicap(securityId);
    }
}
