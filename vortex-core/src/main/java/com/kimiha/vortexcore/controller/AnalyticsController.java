package com.kimiha.vortexcore.controller;

import com.kimiha.vortexcore.model.AnalyticsMetrics;
import com.kimiha.vortexcore.service.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {
    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/metrics")
    public AnalyticsMetrics getMetrics() {
        return analyticsService.getMetrics();
    }
}
