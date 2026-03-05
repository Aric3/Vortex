package com.kimiha.vortexcore.quotation.source;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.kimiha.vortexcore.config.QuotationProperties;
import com.kimiha.vortexcore.model.QuoteSnapshot;
import com.kimiha.vortexcore.quotation.cache.QuotationCache;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模拟行情引擎：在已有缓存快照（或配置初始价）基础上，按间隔推进生成符合典型走势的 tick，写入 QuotationCache
 * 仅在 quotation.source=simulated_only 或 real_then_simulated 时启用
 */
@Service
@ConditionalOnProperty(name = "quotation.enabled", havingValue = "true")
@ConditionalOnExpression("'${quotation.source:real_only}' == 'simulated_only' || '${quotation.source:real_only}' == 'real_then_simulated'")
public class SimulatedQuoteEngine {

    private static final Random RANDOM = new Random();

    private final QuotationProperties quotationProperties;
    private final QuotationCache quoteCache;

    /** 均值回归用：code -> 锚点价（首次见到或配置的初始价） */
    private final Map<String, Double> anchorPriceByCode = new ConcurrentHashMap<>();

    public SimulatedQuoteEngine(QuotationProperties quotationProperties,
                               QuotationCache quoteCache) {
        this.quotationProperties = quotationProperties;
        this.quoteCache = quoteCache;
    }

    @Scheduled(fixedRateString = "${quotation.simulation.interval-ms:1000}", initialDelayString = "${quotation.simulation.interval-ms:1000}")
    public void tick() {
        var sim = quotationProperties.getSimulation();
        var alltick = quotationProperties.getAlltick();
        List<String> symbols = sim.getSymbols().isEmpty() ? alltick.getSymbols() : sim.getSymbols();
        if (symbols.isEmpty()) return;

        long nowMs = System.currentTimeMillis();
        String trend = sim.getTrend() != null ? sim.getTrend() : "flat";
        double volatility = sim.getVolatility();
        double trendStep = sim.getTrendStep();
        double meanReversionRate = Math.max(0.01, Math.min(0.99, sim.getMeanReversionRate()));
        Map<String, Double> initialPrice = sim.getInitialPrice();
        double defaultPrice = sim.getDefaultInitialPrice();

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
            nextPrice = roundToDecimals(nextPrice, priceDecimalsForCode(code));
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

    private static double roundToDecimals(double value, int decimals) {
        if (decimals <= 0) return Math.round(value);
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    /** A 股(.SH/.SZ)、美股(.US) 2 位，港股(.HK) 3 位，其他默认 2 位 */
    private static int priceDecimalsForCode(String code) {
        if (code == null) return 2;
        return code.endsWith(".HK") ? 3 : 2;
    }
}
