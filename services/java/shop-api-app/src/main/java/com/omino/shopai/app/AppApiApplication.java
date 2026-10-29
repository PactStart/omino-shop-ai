package com.omino.shopai.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.omino.shopai.app",
        "com.omino.shopai.api",
        "com.omino.shopai.config"
})
/**
 * APP 进程入口。上下文路径是 /api/app。
 */
public class AppApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppApiApplication.class, args);
    }
}
