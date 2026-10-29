package com.omino.shopai.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
/**
 * 对话工人用的线程池和超时。
 */

@Configuration
public class ChatConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    Executor chatWorkerExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    Duration chatRunTimeout(@Value("${chat.run-timeout-seconds:120}") long seconds) {
        return Duration.ofSeconds(seconds);
    }
}
