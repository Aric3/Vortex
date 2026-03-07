package com.kimiha.vortexcore.quotation.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kimiha.vortexcore.model.HandicapSnapshot;
import com.kimiha.vortexcore.model.QuoteLevel;
import com.kimiha.vortexcore.model.TickSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 行情本地缓存（Caffeine）：最新价与买卖五档分存，互不混合
 * <ul>
 *   <li>tickCache：最新成交价（逐笔 22998 / 模拟引擎）</li>
 *   <li>handicapCache：买卖五档（盘口 22999）</li>
 * </ul>
 */
@Component
public class QuotationCache {

    private static final int MAX_SIZE = 10_000;
    private static final long EXPIRE_MINUTES = 30;
    private static final String[] SUFFIXES = { ".SH", ".SZ", ".BJ", ".HK", ".US"};

    private final Cache<String, TickSnapshot> tickCache = Caffeine.newBuilder()
            .maximumSize(MAX_SIZE)
            .expireAfterWrite(EXPIRE_MINUTES, TimeUnit.MINUTES)
            .build();

    private final Cache<String, HandicapSnapshot> handicapCache = Caffeine.newBuilder()
            .maximumSize(MAX_SIZE)
            .expireAfterWrite(EXPIRE_MINUTES, TimeUnit.MINUTES)
            .build();

    /** 写入最新成交价（仅更新 tick 缓存） */
    public void putTick(String code, double lastPrice, long volume, long tickTimeMs) {
        tickCache.put(code, new TickSnapshot(code, lastPrice, volume, tickTimeMs));
    }

    /** 写入买卖五档（仅更新 handicap 缓存） */
    public void putHandicap(String code, List<QuoteLevel> bids, List<QuoteLevel> asks, long tickTimeMs) {
        handicapCache.put(code, new HandicapSnapshot(code, bids, asks, tickTimeMs));
    }

    public TickSnapshot getTick(String code) {
        return tickCache.getIfPresent(code);
    }

    public HandicapSnapshot getHandicap(String code) {
        return handicapCache.getIfPresent(code);
    }

    /** 按 securityId 解析 code 后取 tick；支持 600030 或 600030.SH 等形式 */
    public TickSnapshot getTickBySecurityId(String securityId) {
        if (securityId == null || securityId.isBlank()) return null;
        String s = securityId.trim();
        if (s.endsWith(".SH") || s.endsWith(".SZ") || s.endsWith(".BJ")) {
            TickSnapshot t = tickCache.getIfPresent(s);
            if (t != null) return t;
        }
        for (String suffix : SUFFIXES) {
            TickSnapshot t = tickCache.getIfPresent(s + suffix);
            if (t != null) return t;
        }
        return null;
    }

    /** 按 securityId 解析 code 后取 handicap */
    public HandicapSnapshot getHandicapBySecurityId(String securityId) {
        if (securityId == null || securityId.isBlank()) return null;
        String s = securityId.trim();
        if (s.endsWith(".SH") || s.endsWith(".SZ") || s.endsWith(".BJ")) {
            HandicapSnapshot h = handicapCache.getIfPresent(s);
            if (h != null) return h;
        }
        for (String suffix : SUFFIXES) {
            HandicapSnapshot h = handicapCache.getIfPresent(s + suffix);
            if (h != null) return h;
        }
        return null;
    }

    public TickSnapshot getTickBySecurityIdAndMarket(String securityId, String market) {
        String suffix = switch (market != null ? market : "") {
            case "XSHG" -> ".SH";
            case "XSHE" -> ".SZ";
            case "BJSE" -> ".BJ";
            case "HKEX" -> ".HK";
            case "US" -> ".US";
            default -> "";
        };
        return getTickBySecurityId(securityId + suffix);
    }

    public HandicapSnapshot getHandicapBySecurityIdAndMarket(String securityId, String market) {
        String suffix = switch (market != null ? market : "") {
            case "XSHG" -> ".SH";
            case "XSHE" -> ".SZ";
            case "BJSE" -> ".BJ";
            case "HKEX" -> ".HK";
            case "US" -> ".US";
            default -> "";
        };
        return getHandicapBySecurityId(securityId + suffix);
    }
}
