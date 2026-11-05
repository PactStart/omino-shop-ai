package com.omino.shopai.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 订单追赶用的线程。一次追赶可能跨很多段，不占触发它的 HTTP 线程。
 */
@Configuration
public class OrderSyncConfig {

    /**
     * 每个平台一轮追赶占一条虚拟线程。
     *
     * @return 追赶线程
     */
    @Bean(name = "orderSyncExecutor")
    Executor orderSyncExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
