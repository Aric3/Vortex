package com.kimiha.vortexcore.alltick;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模拟行情引擎：在已有缓存快照（或配置初始价）基础上，按间隔推进生成符合典型走势的 tick，写入 AllTickQuoteCache。
 * 仅在 alltick.mode=simulated_only 或 real_then_simulated 时启用。
 */
@Service
@ConditionalOnProperty(name = "alltick.enabled", havingValue = "true")
@ConditionalOnExpression("'${alltick.mode:real_only}' == 'simulated_only' || '${alltick.mode:real_only}' == 'real_then_simulated'")
public class SimulatedQuoteEngine {

    private static final Random RANDOM = new Random();

    private final AllTickProperties allTickProperties;
    private final SimulationProperties simulationProperties;
    private final AllTickQuoteCache quoteCache;

    /** 均值回归用：code -> 锚点价（首次见到或配置的初始价） */
    private final Map<String, Double> anchorPriceByCode = new ConcurrentHashMap<>();

    public SimulatedQuoteEngine(AllTickProperties allTickProperties,
                               SimulationProperties simulationProperties,
                               AllTickQuoteCache quoteCache) {
        this.allTickProperties = allTickProperties;
        this.simulationProperties = simulationProperties;
        this.quoteCache = quoteCache;
    }

    @Scheduled(fixedRateString = "${simulation.interval-ms:1000}", initialDelayString = "${simulation.interval-ms:1000}")
    public void tick() {
        List<String> symbols = simulationProperties.getSymbols().isEmpty()
                ? allTickProperties.getSymbols()
                : simulationProperties.getSymbols();
        if (symbols.isEmpty()) return;

        long nowMs = System.currentTimeMillis();
        String trend = simulationProperties.getTrend() != null ? simulationProperties.getTrend() : "flat";
        double volatility = simulationProperties.getVolatility();
        double trendStep = simulationProperties.getTrendStep();
        double meanReversionRate = Math.max(0.01, Math.min(0.99, simulationProperties.getMeanReversionRate()));
        Map<String, Double> initialPrice = simulationProperties.getInitialPrice();
        double defaultPrice = simulationProperties.getDefaultInitialPrice();

        for (String code : symbols) {
            QuoteSnapshot current = quoteCache.get(code);
            double lastPrice;
            long lastVolume;
            if (current != null) {
                lastPrice = current.getLastPrice();
                lastVolume = current.getVolume();
                anchorPriceByCode.putIfAbsent(code, lastPrice);
            } else {
                lastPrice = initialPrice.getOrDefault(code, defaultPrice);
                lastVolume = 0L;
                anchorPriceByCode.put(code, lastPrice);
            }

            double nextPrice = computeNextPrice(trend, lastPrice, anchorPriceByCode.get(code), volatility, trendStep, meanReversionRate);
            if (nextPrice <= 0) nextPrice = lastPrice;
            long nextVolume = lastVolume + (long) (RANDOM.nextInt(100) + 10);

            QuoteSnapshot snapshot = new QuoteSnapshot(code, nextPrice, nextVolume, nowMs);
            quoteCache.put(code, snapshot);
        }
    }

    private double computeNextPrice(String trend, double lastPrice, double anchorOrMean,
                                   double volatility, double trendStep, double meanReversionRate) {
        double noise = (RANDOM.nextDouble() - 0.5) * 2 * volatility;
        return switch (trend != null ? trend.toLowerCase() : "flat") {
            case "trend_up" -> lastPrice + trendStep + noise;
            case "trend_down" -> lastPrice - trendStep + noise;
            case "mean_reversion" -> lastPrice + meanReversionRate * (anchorOrMean - lastPrice) + noise;
            case "random_walk" -> {
                double z = (RANDOM.nextGaussian() * volatility);
                yield lastPrice + z;
            }
            default -> lastPrice + noise; // flat
        };
    }
}
