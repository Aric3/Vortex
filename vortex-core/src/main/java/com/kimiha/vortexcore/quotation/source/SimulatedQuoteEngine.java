package com.kimiha.vortexcore.quotation.source;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.kimiha.vortexcore.config.QuotationProperties;
import com.kimiha.vortexcore.model.QuoteLevel;
import com.kimiha.vortexcore.model.TickSnapshot;
import com.kimiha.vortexcore.quotation.cache.QuotationCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模拟行情引擎：在已有缓存快照（或配置初始价）基础上，按间隔推进生成 tick，写入 QuotationCache
 * 价格走势为简化金融模型，可配置为横盘、随机游走、均值回归或线性趋势，用于测试与演示
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

        String subMode = sim.getSubscriptionMode() != null ? sim.getSubscriptionMode() : "both";
        for (String code : symbols) {
            TickSnapshot current = quoteCache.getTick(code);
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

            if ("tick".equals(subMode) || "both".equals(subMode)) {
                quoteCache.putTick(code, nextPrice, nextVolume, nowMs);
            }
            if ("both".equals(subMode)) {
                List<QuoteLevel> bids = buildSimulatedBids(nextPrice, code);
                List<QuoteLevel> asks = buildSimulatedAsks(nextPrice, code);
                quoteCache.putHandicap(code, bids, asks, nowMs);
            }
        }
    }

    /** 生成模拟买盘五档：买一略低于 lastPrice，依次下探 */
    private List<QuoteLevel> buildSimulatedBids(double lastPrice, String code) {
        double tickSize = tickSizeForCode(code);
        int decimals = priceDecimalsForCode(code);
        List<QuoteLevel> bids = new ArrayList<>(5);
        for (int i = 1; i <= 5; i++) {
            double price = roundToDecimals(lastPrice - i * tickSize, decimals);
            if (price <= 0) break;
            long volume = baseVolumeForCode(code) + RANDOM.nextInt(500);
            bids.add(new QuoteLevel(price, volume));
        }
        return bids;
    }

    /** 生成模拟卖盘五档：卖一略高于 lastPrice，依次上探 */
    private List<QuoteLevel> buildSimulatedAsks(double lastPrice, String code) {
        double tickSize = tickSizeForCode(code);
        int decimals = priceDecimalsForCode(code);
        List<QuoteLevel> asks = new ArrayList<>(5);
        for (int i = 1; i <= 5; i++) {
            double price = roundToDecimals(lastPrice + i * tickSize, decimals);
            long volume = baseVolumeForCode(code) + RANDOM.nextInt(500);
            asks.add(new QuoteLevel(price, volume));
        }
        return asks;
    }

    /** 最小变动单位：A股/美股 0.01，港股 0.01 */
    private static double tickSizeForCode(String code) {
        return 0.01;
    }

    /** 每档基准量：A股 100 手起，港股/美股按价格适当放大 */
    private long baseVolumeForCode(String code) {
        if (code == null) return 100;
        if (code.endsWith(".HK")) return 100;
        if (code.endsWith(".US")) return 100;
        return 100;
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
