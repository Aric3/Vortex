package com.kimiha.vortexcore.controller;

import com.kimiha.vortexcore.model.AnalyticsMetrics;
import com.kimiha.vortexcore.model.Result;
import com.kimiha.vortexcore.service.AnalyticsService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {
    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * GET /api/v1/analytics/metrics — 返回当前指标快照（JSON），供前端风控指标页拉取。
     */
    @GetMapping("/metrics")
    public AnalyticsMetrics metrics() {
        try {
            AnalyticsMetrics m = analyticsService.getMetrics();
            return m != null ? m : new AnalyticsMetrics(0, 0, null, java.util.List.of(), System.currentTimeMillis());
        } catch (Exception e) {
            return new AnalyticsMetrics(0, 0, null, java.util.List.of(), System.currentTimeMillis());
        }
    }

    @GetMapping(value = "/metrics/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Result> streamMetrics() {
        return analyticsService.streamMetrics()
                .map(metrics -> Result.success(metrics, "Metrics stream update"));
    }
}
