package com.omino.shopai.admin.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
/**
 * 签发带 gv=1 的短时令牌。
 */

public final class GodViewIssuer {

    public static final int TTL_SECONDS = 3600;

    private final byte[] secret;
/**
 * 用与 APP 相同的密钥签短时 JWT，claim 里带 gv=1。
 */

    public GodViewIssuer(String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < 32) {
            throw new IllegalArgumentException("JWT_ACCESS_SECRET 至少 32 字节");
        }
    }
/**
 * 签发短期凭证。
 */

    public String issue(String userId) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(userId)
                    .claim("gv", 1)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(TTL_SECONDS)))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (Exception ex) {
            throw new IllegalStateException("签发凭证失败");
        }
    }
}
