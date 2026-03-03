package com.kimiha.vortexcore.alltick;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 集成测试：alltick.mode=simulated_only 时 SimulatedQuoteEngine 写入缓存，MarketDataService 可读到行情。
 */
@SpringBootTest(properties = {
        "alltick.enabled=true",
        "alltick.mode=simulated_only",
        "alltick.symbols[0]=600030.SH",
        "alltick.token=test-token",
        "simulation.interval-ms=500",
        "simulation.trend=flat",
        "simulation.default-initial-price=10.0"
})
class SimulatedQuoteIntegrationTest {

    @Autowired(required = false)
    private MarketDataService marketDataService;

    @Autowired(required = false)
    private SimulatedQuoteEngine simulatedQuoteEngine;

    @Test
    void whenSimulatedOnly_enginePresent_andQuoteAvailableAfterTicks() throws InterruptedException {
        assertNotNull(simulatedQuoteEngine, "SimulatedQuoteEngine should be created when mode=simulated_only");
        assertNotNull(marketDataService);
        simulatedQuoteEngine.tick();
        QuoteSnapshot q = marketDataService.getQuote("600030");
        assertNotNull(q, "Quote should be available after tick (600030 -> 600030.SH)");
        assertTrue(q.getLastPrice() > 0);
    }
}
