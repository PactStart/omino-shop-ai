package com.omino.shopai.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.omino.shopai.admin",
        "com.omino.shopai.api",
        "com.omino.shopai.config"
})
/**
 * 管理端进程入口。上下文路径是 /api/admin。
 */
public class AdminApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApiApplication.class, args);
    }
}
