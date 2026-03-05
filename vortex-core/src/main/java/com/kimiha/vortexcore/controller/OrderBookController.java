package com.kimiha.vortexcore.controller;

import com.kimiha.vortexcore.matching.MatchingEngine;
import com.kimiha.vortexcore.matching.OrderBookSnapshot;
import com.kimiha.vortexcore.model.Result;
import com.kimiha.vortexcore.service.OrderBookStreamService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/vclient")
public class OrderBookController {

    private static final int DEFAULT_DEPTH = 10;

    private final MatchingEngine matchingEngine;
    private final OrderBookStreamService streamService;

    public OrderBookController(MatchingEngine matchingEngine, OrderBookStreamService streamService) {
        this.matchingEngine = matchingEngine;
        this.streamService = streamService;
    }

    /**
     * 查询订单簿快照（轮询用）
     * GET /api/v1/vclient/orderbook/{securityId}?depth=10
     */
    @GetMapping("/orderbook/{securityId}")
    public Result getSnapshot(
            @PathVariable String securityId,
            @RequestParam(defaultValue = "10") int depth) {
        if (depth <= 0 || depth > 50) depth = DEFAULT_DEPTH;
        OrderBookSnapshot snapshot = matchingEngine.getOrderBook(securityId).getPublishedSnapshot(depth);
        return Result.success(snapshot);
    }

    /**
     * 实时订单簿流：连接后先收到当前快照，之后每次变动推送新快照（SSE）
     * GET /api/v1/vclient/orderbook/{securityId}/stream?depth=10
     */
    @GetMapping(value = "/orderbook/{securityId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<OrderBookSnapshot> stream(@PathVariable String securityId,@RequestParam(defaultValue = "10") int depth) {
        if (depth <= 0 || depth > 50) depth = DEFAULT_DEPTH;
        return streamService.stream(securityId, depth);
    }
}
