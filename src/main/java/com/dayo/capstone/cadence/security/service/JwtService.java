package com.dayo.capstone.cadence.security.service;

import com.dayo.capstone.cadence.security.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final RSAPrivateKey rsaPrivateKey;
    private final RSAPublicKey rsaPublicKey;
    private final JwtProperties jwtProperties;

    /**
     * PRD Section 3 — roles is a claims ARRAY, not a single string, because
     * a user can hold multiple roles simultaneously (e.g. FACILITATOR +
     * ADMIN). Every consumer of this token must check membership in
     * the array, never assume a single value.
     *
     * Multi-tenant: "inst" is the institution this token acts in (null for a
     * platform admin who hasn't entered one) and "roles" are the roles in THAT
     * institution only. "pa" marks a platform admin. JwtAuthFilter re-reads the
     * roles from the database on every request; the claim is informational.
     */
    public String generateAccessToken(UUID userPublicId, Set<String> roles, UUID institutionPublicId, boolean platformAdmin) {
        long now = System.currentTimeMillis();

        return Jwts.builder()
                .subject(userPublicId.toString())
                .claim("roles", roles.stream().toList())
                .claim("inst", institutionPublicId != null ? institutionPublicId.toString() : null)
                .claim("pa", platformAdmin)
                .claim("jti", UUID.randomUUID().toString())
                .issuedAt(new Date(now))
                .expiration(new Date(now + jwtProperties.getAccessTokenExpiryMs()))
                .signWith(rsaPrivateKey, Jwts.SIG.RS256)
                .header().keyId("cadence-key-v1").and()
                .compact();
    }

    /** Raw refresh token — the caller hashes this before persisting; never store raw. */
    public String generateRefreshTokenRaw() {
        return UUID.randomUUID().toString() + UUID.randomUUID();
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash token", e);
        }
    }

    public Claims verifyToken(String token) {
        return Jwts.parser()
                .verifyWith(rsaPublicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Dormant until a second consumer exists (Section 5's extraction-path philosophy). */
    public java.util.Map<String, Object> getPublicKeyAsJwks() {
        String base64 = Base64.getUrlEncoder().withoutPadding().encodeToString(rsaPublicKey.getEncoded());
        return java.util.Map.of(
                "keys", new Object[]{
                        java.util.Map.of(
                                "kty", "RSA", "use", "sig", "alg", "RS256",
                                "kid", "cadence-key-v1", "key", base64
                        )
                }
        );
    }

    public String extractJti(Claims claims) {
        return claims.get("jti", String.class);
    }

    public java.util.Date extractExpiration(Claims claims) {
        return claims.getExpiration();
    }
}