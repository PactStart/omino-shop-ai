package com.omino.shopai.app.util;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 记录官方客户端被创建的次数，用来确认缺密钥时没有出站。
 */
public final class SdkSessions {

    public static final AtomicInteger opened = new AtomicInteger();

    private SdkSessions() {
    }

    /** 真正 new 官方客户端时记一次。 */
    public static void markOpened() {
        opened.incrementAndGet();
    }
}
