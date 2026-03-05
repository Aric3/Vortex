package com.kimiha.vortexcore.service;

import org.springframework.stereotype.Service;

import com.kimiha.vortexcore.model.HandicapSnapshot;
import com.kimiha.vortexcore.model.TickSnapshot;
import com.kimiha.vortexcore.quotation.cache.QuotationCache;

/**
 * 行情查询：从缓存读取最新价（tick）与买卖五档（handicap），二者分开，供撮合/风控使用。
 */
@Service
public class QuotationService {

    private final QuotationCache quoteCache;

    public QuotationService(QuotationCache quoteCache) {
        this.quoteCache = quoteCache;
    }

    public TickSnapshot getTick(String securityId) {
        return quoteCache.getTickBySecurityId(securityId);
    }

    public TickSnapshot getTick(String securityId, String market) {
        return quoteCache.getTickBySecurityIdAndMarket(securityId, market);
    }

    public TickSnapshot getTickByCode(String code) {
        return quoteCache.getTick(code);
    }

    public HandicapSnapshot getHandicap(String securityId) {
        return quoteCache.getHandicapBySecurityId(securityId);
    }

    public HandicapSnapshot getHandicap(String securityId, String market) {
        return quoteCache.getHandicapBySecurityIdAndMarket(securityId, market);
    }

    public HandicapSnapshot getHandicapByCode(String code) {
        return quoteCache.getHandicap(code);
    }
}
