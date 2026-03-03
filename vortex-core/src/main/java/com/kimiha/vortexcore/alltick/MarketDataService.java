package com.kimiha.vortexcore.alltick;

import org.springframework.stereotype.Service;

/**
 * 行情查询：从 Caffeine 缓存读取 AllTick 写入的最新价等，供撮合/风控使用。
 * 热路径只读内存，无网络 IO。
 */
@Service
public class MarketDataService {

    private final AllTickQuoteCache quoteCache;

    public MarketDataService(AllTickQuoteCache quoteCache) {
        this.quoteCache = quoteCache;
    }

    /**
     * 按本系统 securityId（如 600030）查行情，若有多市场则优先返回缓存中存在的。
     */
    public QuoteSnapshot getQuote(String securityId) {
        return quoteCache.getBySecurityId(securityId);
    }

    /**
     * 按 AllTick code（如 600030.SH）查行情。
     */
    public QuoteSnapshot getQuoteByCode(String code) {
        return quoteCache.get(code);
    }
}
