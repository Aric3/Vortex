package com.kimiha.vortexcore.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.AccessLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 行情来源统一配置：alltick（真实）与 simulation（模拟）并列。
 * source: real_only=仅真实 | simulated_only=仅模拟 | real_then_simulated=先真实再由模拟推进
 */
@ConfigurationProperties(prefix = "quotation")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuotationProperties {

    /** 是否启用行情模块 */
    private boolean enabled = true;

    /**
     * 行情来源：real_only | simulated_only | real_then_simulated
     */
    private String source = "real_only";

    @NestedConfigurationProperty
    private AllTickSourceProperties alltick = new AllTickSourceProperties();

    @NestedConfigurationProperty
    private SimulationSourceProperties simulation = new SimulationSourceProperties();

    /** AllTick 真实行情源配置 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AllTickSourceProperties {
        private String quoteUrl = "wss://quote.alltick.co/quote-stock-b-ws-api";
        private String token = "";
        private List<String> symbols = new ArrayList<>();
        private long heartbeatIntervalMs = 10_000;
        private long reconnectIntervalMs = 5_000;
    }

    /** 模拟行情源配置 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimulationSourceProperties {
        private long intervalMs = 1000;
        private double volatility = 0.01;
        private String trend = "flat";
        private double trendStep = 0.02;
        private double meanReversionRate = 0.1;
        private List<String> symbols = new ArrayList<>();
        @Setter(AccessLevel.NONE)
        private Map<String, Double> initialPrice = Map.of();
        private double defaultInitialPrice = 10.0;

        public void setInitialPrice(Map<String, Double> initialPrice) {
            this.initialPrice = initialPrice != null ? initialPrice : Map.of();
        }
    }
}
