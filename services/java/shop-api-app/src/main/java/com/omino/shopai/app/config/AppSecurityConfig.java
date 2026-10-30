package com.omino.shopai.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.app.security.AppJwtFilter;
import com.omino.shopai.app.security.ClientHeaderFilter;
import com.omino.shopai.app.service.auth.AppJwtIssuer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
/**
 * APP 安全链和事务模板。
 */

@Configuration
public class AppSecurityConfig {

    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
        return new TransactionTemplate(manager);
    }
/**
 * APP 自己验 JWT，并提供事务模板给同步任务。
 */

    @Bean
    SecurityFilterChain appSecurity(HttpSecurity http, AppJwtIssuer tokens, ObjectMapper objectMapper) throws Exception {
        AppJwtFilter jwt = new AppJwtFilter(tokens, objectMapper);
        ClientHeaderFilter clients = new ClientHeaderFilter(objectMapper);
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(clients, AppJwtFilter.class);
        return http.build();
    }
}
