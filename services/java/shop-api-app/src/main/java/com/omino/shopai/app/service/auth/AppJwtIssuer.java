package com.omino.shopai.app.service.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
/**
 * 签发和校验 APP 访问令牌。刷新令牌不放进响应日志。
 */

@Component
public class AppJwtIssuer {

    private final byte[] secret;
    private final long ttlSeconds;

    public AppJwtIssuer(
            @Value("${JWT_ACCESS_SECRET:}") String secret,
            @Value("${JWT_ACCESS_TTL_SECONDS:1800}") long ttlSeconds) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
        if (this.secret.length < 32) {
            throw new IllegalStateException("JWT_ACCESS_SECRET 至少 32 字节");
        }
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }
/**
 * 签发短期凭证。
 */

    public String issue(String userId) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(userId)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(ttlSeconds)))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException("签发凭证失败", ex);
        }
    }
/**
 * 从令牌取出用户主键。签错或过期则当未登录。
 */

    /**
     * 判断这张访问令牌是不是上帝视角。签错或没有 gv 都当作普通用户。
     *
     * @param token 访问令牌
     * @return 带 gv=1 时为真
     */
    public boolean godView(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(secret))) {
                return false;
            }
            Object claim = jwt.getJWTClaimsSet().getClaim("gv");
            return claim instanceof Number number && number.intValue() == 1;
        } catch (ParseException | JOSEException ex) {
            return false;
        }
    }

    public Optional<String> subject(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(secret))) {
                return Optional.empty();
            }
            Date expiration = jwt.getJWTClaimsSet().getExpirationTime();
            if (expiration == null || !expiration.after(new Date())) {
                return Optional.empty();
            }
            String subject = jwt.getJWTClaimsSet().getSubject();
            return subject == null || subject.isBlank() ? Optional.empty() : Optional.of(subject);
        } catch (ParseException | JOSEException ex) {
            return Optional.empty();
        }
    }
}
