package com.kimiha.vortexcore.alltick;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "alltick")
public class AllTickProperties {

    /** 是否启用 AllTick 行情接入 */
    private boolean enabled = false;

    /** WebSocket 地址，A 股使用股票接口 */
    private String quoteUrl = "wss://quote.alltick.co/quote-stock-b-ws-api";

    /** 认证 token，建议通过环境变量 ALLTICK_TOKEN 配置 */
    private String token = "";

    /** 订阅的标的 AllTick code 列表，如 600030.SH、000001.SZ */
    private List<String> symbols = new ArrayList<>();

    /** 心跳间隔（毫秒），AllTick 要求约 10 秒内发送一次 */
    private long heartbeatIntervalMs = 10_000;

    /** 断线重连间隔（毫秒） */
    private long reconnectIntervalMs = 5_000;

    /**
     * 行情模式：real_only=仅真实拉取；simulated_only=仅模拟；real_then_simulated=先真实拉一次再由模拟覆盖。
     */
    private String mode = "real_only";

    /** HTTP 快照刷新间隔（毫秒），0=仅启动时拉一次，>0=定时刷新 */
    private long httpRefreshIntervalMs = 0;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getQuoteUrl() {
        return quoteUrl;
    }

    public void setQuoteUrl(String quoteUrl) {
        this.quoteUrl = quoteUrl;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public List<String> getSymbols() {
        return symbols;
    }

    public void setSymbols(List<String> symbols) {
        this.symbols = symbols;
    }

    public long getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public long getReconnectIntervalMs() {
        return reconnectIntervalMs;
    }

    public void setReconnectIntervalMs(long reconnectIntervalMs) {
        this.reconnectIntervalMs = reconnectIntervalMs;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public long getHttpRefreshIntervalMs() {
        return httpRefreshIntervalMs;
    }

    public void setHttpRefreshIntervalMs(long httpRefreshIntervalMs) {
        this.httpRefreshIntervalMs = httpRefreshIntervalMs;
    }
}
