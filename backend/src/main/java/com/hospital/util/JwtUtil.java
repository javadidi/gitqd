package com.hospital.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Component
public class JwtUtil {

    /** 区分「员工 token」和「小程序用户 token」，两者 claims 形状不同，不能混用 */
    public static final String CLAIM_PRINCIPAL = "principal";
    public static final String PRINCIPAL_ADMIN = "admin";
    public static final String PRINCIPAL_USER = "user";

    private final SecretKey key;
    private final long expiration;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration}") long expiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    public String generateToken(Long adminId, String username, String roleName,
                                List<String> modules, List<String> caps) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(String.valueOf(adminId))
                .claim(CLAIM_PRINCIPAL, PRINCIPAL_ADMIN)
                .claim("username", username)
                .claim("role", roleName)
                .claim("modules", modules)
                .claim("caps", caps)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * 小程序用户 token：sub=user.id。
     * 刻意不放 role/modules/caps——患者不走 RBAC 那套模块权限，
     * 归属校验一律靠 sub 里的 userId（见 SecurityUtils.currentUserId）。
     */
    public String generateUserToken(Long userId, String openid) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_PRINCIPAL, PRINCIPAL_USER)
                .claim("openid", openid)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}
