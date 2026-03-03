package com.kimiha.vortexcore.alltick;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模拟引擎：走势方向、均值回归、缓存更新。
 */
class SimulatedQuoteEngineTest {

    private static final String CODE = "600030.SH";
    private AllTickProperties allTickProperties;
    private SimulationProperties simulationProperties;
    private AllTickQuoteCache quoteCache;
    private SimulatedQuoteEngine engine;

    @BeforeEach
    void setUp() {
        allTickProperties = new AllTickProperties();
        allTickProperties.setSymbols(List.of(CODE));
        simulationProperties = new SimulationProperties();
        simulationProperties.setSymbols(List.of());
        simulationProperties.setVolatility(0.001);
        simulationProperties.setTrendStep(0.05);
        simulationProperties.setMeanReversionRate(0.2);
        simulationProperties.setDefaultInitialPrice(10.0);
        quoteCache = new AllTickQuoteCache();
        engine = new SimulatedQuoteEngine(allTickProperties, simulationProperties, quoteCache);
    }

    @Test
    void trendUp_multipleTicks_priceIncreases() {
        simulationProperties.setTrend("trend_up");
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
        simulationProperties.setTrend("trend_down");
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
        simulationProperties.setTrend("mean_reversion");
        simulationProperties.setVolatility(0);
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
        simulationProperties.setTrend("flat");
        simulationProperties.setDefaultInitialPrice(27.5);
        engine.tick();
        QuoteSnapshot snap = quoteCache.get(CODE);
        assertNotNull(snap);
        assertEquals(CODE, snap.getCode());
        assertTrue(snap.getLastPrice() >= 27.0 && snap.getLastPrice() <= 28.0,
                "flat with low volatility: price near default 27.5");
    }

    @Test
    void symbolsFromSimulationWhenSet_otherwiseFromAlltick() {
        simulationProperties.setSymbols(List.of("000001.SZ"));
        allTickProperties.setSymbols(List.of(CODE));
        engine.tick();
        assertNotNull(quoteCache.get("000001.SZ"));
    }
}
