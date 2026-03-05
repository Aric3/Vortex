package com.kimiha.vortexcore.quotation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kimiha.vortexcore.config.QuotationProperties;
import com.kimiha.vortexcore.model.QuoteSnapshot;
import com.kimiha.vortexcore.quotation.cache.QuotationCache;
import com.kimiha.vortexcore.quotation.source.SimulatedQuoteEngine;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模拟引擎：走势方向、均值回归、缓存更新。
 */
class SimulatedQuoteEngineTest {

    private static final String CODE = "600030.SH";
    private QuotationProperties quotationProperties;
    private QuotationCache quoteCache;
    private SimulatedQuoteEngine engine;

    @BeforeEach
    void setUp() {
        quotationProperties = new QuotationProperties();
        quotationProperties.setEnabled(true);
        quotationProperties.setSource("simulated_only");
        quotationProperties.getAlltick().setSymbols(List.of(CODE));
        quotationProperties.getSimulation().setSymbols(List.of());
        quotationProperties.getSimulation().setVolatility(0.001);
        quotationProperties.getSimulation().setTrendStep(0.05);
        quotationProperties.getSimulation().setMeanReversionRate(0.2);
        quotationProperties.getSimulation().setDefaultInitialPrice(10.0);
        quoteCache = new QuotationCache();
        engine = new SimulatedQuoteEngine(quotationProperties, quoteCache);
    }

    @Test
    void trendUp_multipleTicks_priceIncreases() {
        quotationProperties.getSimulation().setTrend("trend_up");
        quoteCache.put(CODE, new QuoteSnapshot(CODE, 10.0, 0L, 0L));
        double first = quoteCache.get(CODE).getLastPrice();
        for (int i = 0; i < 20; i++) {
            engine.tick();
        }
        QuoteSnapshot after = quoteCache.get(CODE);
        assertNotNull(after);
        assertTrue(after.getLastPrice() > first, "trend_up: price should increase");
    }

    @Test
    void trendDown_multipleTicks_priceDecreases() {
        quotationProperties.getSimulation().setTrend("trend_down");
        quoteCache.put(CODE, new QuoteSnapshot(CODE, 10.0, 0L, 0L));
        double first = quoteCache.get(CODE).getLastPrice();
        for (int i = 0; i < 20; i++) {
            engine.tick();
        }
        QuoteSnapshot after = quoteCache.get(CODE);
        assertNotNull(after);
        assertTrue(after.getLastPrice() < first, "trend_down: price should decrease");
    }

    @Test
    void meanReversion_priceMovesTowardAnchor() {
        quotationProperties.getSimulation().setTrend("mean_reversion");
        quotationProperties.getSimulation().setVolatility(0);
        quoteCache.put(CODE, new QuoteSnapshot(CODE, 100.0, 0L, 0L));
        engine.tick();
        quoteCache.put(CODE, new QuoteSnapshot(CODE, 150.0, 0L, 0L));
        engine.tick();
        QuoteSnapshot after = quoteCache.get(CODE);
        assertNotNull(after);
        assertTrue(after.getLastPrice() < 150 && after.getLastPrice() >= 100,
                "mean_reversion: price should move toward 100 from 150");
    }

    @Test
    void flat_noInitialCache_usesDefaultInitialPrice() {
        quotationProperties.getSimulation().setTrend("flat");
        quotationProperties.getSimulation().setDefaultInitialPrice(27.5);
        engine.tick();
        QuoteSnapshot snap = quoteCache.get(CODE);
        assertNotNull(snap);
        assertEquals(CODE, snap.getCode());
        assertTrue(snap.getLastPrice() >= 27.0 && snap.getLastPrice() <= 28.0,
                "flat with low volatility: price near default 27.5");
    }

    @Test
    void symbolsFromSimulationWhenSet_otherwiseFromAlltick() {
        quotationProperties.getSimulation().setSymbols(List.of("000001.SZ"));
        quotationProperties.getAlltick().setSymbols(List.of(CODE));
        engine.tick();
        assertNotNull(quoteCache.get("000001.SZ"));
    }
}
