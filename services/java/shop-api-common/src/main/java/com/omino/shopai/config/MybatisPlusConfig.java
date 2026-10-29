package com.omino.shopai.config;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusPropertiesCustomizer;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.omino.shopai.mapper")
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusPropertiesCustomizer mapperLocations() {
        return properties -> properties.setMapperLocations(new String[]{"classpath*:/mapper/**/*.xml"});
    }
}
