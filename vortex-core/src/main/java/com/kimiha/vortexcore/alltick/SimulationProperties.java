package com.kimiha.vortexcore.alltick;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "simulation")
public class SimulationProperties {

    /** 模拟 tick 推进间隔（毫秒） */
    private long intervalMs = 1000;

    /** 价格波动率/步长（用于 flat 的 spread、random_walk 等） */
    private double volatility = 0.01;

    /** 走势类型：flat, trend_up, trend_down, mean_reversion, random_walk */
    private String trend = "flat";

    /** 趋势步长（trend_up/trend_down 每步价格变化） */
    private double trendStep = 0.02;

    /** 均值回归速率 (0,1)，mean_reversion 时使用 */
    private double meanReversionRate = 0.1;

    /** 模拟标的 code 列表，空则使用 alltick.symbols */
    private List<String> symbols = new ArrayList<>();

    /** 按 code 的初始价，用于 simulated_only 无缓存时；key 为 AllTick code */
    private Map<String, Double> initialPrice = Map.of();

    /** 默认初始价（当 code 不在 initialPrice 中时） */
    private double defaultInitialPrice = 10.0;

    public long getIntervalMs() {
        return intervalMs;
    }

    public void setIntervalMs(long intervalMs) {
        this.intervalMs = intervalMs;
    }

    public double getVolatility() {
        return volatility;
    }

    public void setVolatility(double volatility) {
        this.volatility = volatility;
    }

    public String getTrend() {
        return trend;
    }

    public void setTrend(String trend) {
        this.trend = trend;
    }

    public double getTrendStep() {
        return trendStep;
    }

    public void setTrendStep(double trendStep) {
        this.trendStep = trendStep;
    }

    public double getMeanReversionRate() {
        return meanReversionRate;
    }

    public void setMeanReversionRate(double meanReversionRate) {
        this.meanReversionRate = meanReversionRate;
    }

    public List<String> getSymbols() {
        return symbols;
    }

    public void setSymbols(List<String> symbols) {
        this.symbols = symbols;
    }

    public Map<String, Double> getInitialPrice() {
        return initialPrice;
    }

    public void setInitialPrice(Map<String, Double> initialPrice) {
        this.initialPrice = initialPrice != null ? initialPrice : Map.of();
    }

    public double getDefaultInitialPrice() {
        return defaultInitialPrice;
    }

    public void setDefaultInitialPrice(double defaultInitialPrice) {
        this.defaultInitialPrice = defaultInitialPrice;
    }
}
