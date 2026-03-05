package com.kimiha.vortexcore.quotation;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.kimiha.vortexcore.model.QuoteSnapshot;
import com.kimiha.vortexcore.quotation.source.SimulatedQuoteEngine;
import com.kimiha.vortexcore.service.QuotationService;

/**
 * 集成测试：quotation.source=simulated_only 时 SimulatedQuoteEngine 写入缓存，QuotationService 可读到行情。
 */
@SpringBootTest(properties = {
        "quotation.enabled=true",
        "quotation.source=simulated_only",
        "quotation.alltick.symbols[0]=600030.SH",
        "quotation.alltick.token=test-token",
        "quotation.simulation.interval-ms=500",
        "quotation.simulation.trend=flat",
        "quotation.simulation.default-initial-price=10.0"
})
class SimulatedQuoteIntegrationTest {

    @Autowired(required = false)
    private QuotationService marketDataService;

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
