package com.omino.shopai.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.web.SecurityFilterChain;
/**
 * 对接 omino-sso 的授权码和 PKCE。不建管理员密码表。
 */

@Configuration
public class AdminOAuth2Config {

    @Bean
    ClientRegistrationRepository adminClients(
            @Value("${SSO_ISSUER:http://127.0.0.1:8080}") String issuer,
            @Value("${SSO_CLIENT_ID:shop-admin}") String clientId,
            @Value("${SSO_CLIENT_SECRET:local-only-placeholder-secret}") String secret,
            @Value("${SSO_REDIRECT_URI:http://127.0.0.1:8082/api/admin/v1/auth/callback}") String redirect) {
        ClientRegistration registration = ClientRegistration.withRegistrationId("omino")
                .clientId(clientId)
                .clientSecret(secret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirect)
                .scope("openid", "profile")
                .authorizationUri(issuer + "/oauth2/authorize")
                .tokenUri(issuer + "/oauth2/token")
                .userInfoUri(issuer + "/oauth2/userinfo")
                .jwkSetUri(issuer + "/oauth2/jwks")
                .userNameAttributeName("sub")
                .build();
        return new InMemoryClientRegistrationRepository(registration);
    }
/**
 * 授权请求带 PKCE，只用 S256。
 */

    @Bean
    OAuth2AuthorizationRequestResolver adminPkce(ClientRegistrationRepository clients) {
        DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(clients, "/oauth2/authorization");
        // Security 6.4 没有 ClientSettings，S256 挂在授权请求上。
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return resolver;
    }
/**
 * 浏览器走授权码登录。接口是否已登录由会话过滤器判断。
 */

    @Bean
    SecurityFilterChain adminSecurity(
            HttpSecurity http,
            OAuth2AuthorizationRequestResolver adminPkce,
            ObjectMapper objectMapper,
            @Value("${shop.admin-web-origin:http://127.0.0.1:5183}") String webOrigin) throws Exception {
        String origin = webOrigin.endsWith("/") ? webOrigin.substring(0, webOrigin.length() - 1) : webOrigin;
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .oauth2Login(oauth -> oauth
                        .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(adminPkce))
                        .redirectionEndpoint(endpoint -> endpoint.baseUri("/v1/auth/callback"))
                        .successHandler((request, response, authentication) -> {
                            // 只记下登录主体。重定向地址不带访问令牌。
                            request.getSession(true).setAttribute("ssoSub", authentication.getName());
                            response.sendRedirect(origin + "/");
                        })
                        .failureHandler((request, response, exception) -> {
                            HttpSession session = request.getSession(false);
                            if (session != null) {
                                session.invalidate();
                            }
                            response.sendRedirect(origin + "/login/callback");
                        }))
                .addFilterBefore(new AdminSessionFilter(objectMapper), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
