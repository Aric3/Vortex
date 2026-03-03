package com.kimiha.vortexcore.controller;

import com.kimiha.vortexcore.alltick.MarketDataService;
import com.kimiha.vortexcore.alltick.QuoteSnapshot;
import com.kimiha.vortexcore.model.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 行情查询：从 AllTick 写入的 Caffeine 缓存读取，用于验证接入与调试
 */
@RestController
@RequestMapping("/api/v1/vclient")
public class QuoteController {

    private final MarketDataService marketDataService;

    public QuoteController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    /**
     * 按 securityId 查缓存行情（如 600030）
     * GET /api/v1/vclient/quote/{securityId}
     */
    @GetMapping("/quote/{securityId}")
    public Result getQuote(@PathVariable String securityId) {
        QuoteSnapshot q = marketDataService.getQuote(securityId);
        return Result.success(q);
    }
}
