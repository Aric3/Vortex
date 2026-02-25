package com.kimiha.vortexcore.service.tools;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 生成 12 字符成交编号，满足 API 规范 execId char[12]。
 */
public final class ExecIdGenerator {
    private static final AtomicInteger sequence = new AtomicInteger(0);
    private static final int MAX_SEQ = 999_999;

    public static String next() {
        int seq = sequence.updateAndGet(n -> n >= MAX_SEQ ? 0 : n + 1);
        String ts = String.valueOf(System.currentTimeMillis());
        String pad = String.format("%06d", seq);
        // 取时间戳后 6 位 + 6 位序号，共 12 字符
        String suffix = ts.length() >= 6 ? ts.substring(ts.length() - 6) : String.format("%6s", ts).replace(' ', '0');
        return suffix + pad;
    }
}
