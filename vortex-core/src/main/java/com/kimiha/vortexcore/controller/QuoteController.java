package com.kimiha.vortexcore.controller;

import com.kimiha.vortexcore.model.HandicapSnapshot;
import com.kimiha.vortexcore.model.Result;
import com.kimiha.vortexcore.model.TickSnapshot;
import com.kimiha.vortexcore.service.QuotationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 行情查询：tick 与 handicap 分两个接口返回。
 */
@RestController
@RequestMapping("/api/v1/vclient")
public class QuoteController {

    private final QuotationService quotationService;

    public QuoteController(QuotationService quotationService) {
        this.quotationService = quotationService;
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
}
