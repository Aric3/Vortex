package com.kimiha.vortexcore.alltick;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * AllTick 行情本地缓存（Caffeine） 
 * WebSocket 推送写入，撮合/风控只读。
 * Key = AllTick code（如 600030.SH），Value = QuoteSnapshot
 */
@Component
public class AllTickQuoteCache {

    private static final int MAX_SIZE = 10_000;
    private static final long EXPIRE_MINUTES = 30;

    private final Cache<String, QuoteSnapshot> cache = Caffeine.newBuilder()
            .maximumSize(MAX_SIZE)
            .expireAfterWrite(EXPIRE_MINUTES, TimeUnit.MINUTES)
            .build();

    public void put(String code, QuoteSnapshot snapshot) {
        cache.put(code, snapshot);
    }

    public QuoteSnapshot get(String code) {
        return cache.getIfPresent(code);
    }

    /**
     * 按本系统 securityId 或 AllTick code 查找行情 会尝试常见后缀 .SH / .SZ / .BJ / .HK / .US
     */
    public QuoteSnapshot getBySecurityId(String securityId) {
        if (securityId == null || securityId.isBlank()) return null;
        String s = securityId.trim();
        QuoteSnapshot q = cache.getIfPresent(s);
        if (q != null) return q;
        for (String suffix : new String[] { ".SH", ".SZ", ".BJ", ".HK", ".US" }) {
            q = cache.getIfPresent(s + suffix);
            if (q != null) return q;
        }
        return null;
    }
}
