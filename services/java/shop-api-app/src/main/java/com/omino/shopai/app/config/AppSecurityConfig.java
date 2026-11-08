package com.omino.shopai.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.app.security.AppJwtFilter;
import com.omino.shopai.app.security.ClientHeaderFilter;
import com.omino.shopai.app.service.auth.AppJwtIssuer;
import com.omino.shopai.entity.AppUser;
import com.omino.shopai.enums.UserStatus;
import com.omino.shopai.mapper.AppUserMapper;
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
     * APP 自己验 JWT。注销或禁用的账号即使令牌未过期也不再放行。
     *
     * @param http 安全链
     * @param tokens 访问令牌
     * @param objectMapper 写 401 信封
     * @param users 用来确认账号仍有效
     * @return 安全链
     */
    @Bean
    SecurityFilterChain appSecurity(
            HttpSecurity http,
            AppJwtIssuer tokens,
            ObjectMapper objectMapper,
            AppUserMapper users) throws Exception {
        AppJwtFilter jwt = new AppJwtFilter(tokens, objectMapper, userId -> stillActive(users, userId));
        ClientHeaderFilter clients = new ClientHeaderFilter(objectMapper);
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(clients, AppJwtFilter.class);
        return http.build();
    }

    /**
     * 账号还在、且没有被禁用，才允许继续用这张 access token。
     *
     * @param users 用户表
     * @param userId 令牌里的用户主键
     * @return 仍可访问时为真
     */
    private static boolean stillActive(AppUserMapper users, String userId) {
        AppUser user = users.selectById(userId);
        return user != null && user.getDeletedAt() == null && user.getStatus() != UserStatus.DISABLED;
    }
}
