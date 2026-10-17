package com.dayo.capstone.cadence.security.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class JwtProperties {
    @Value("${app.jwt.private-key-path}")
    private String privateKeyPath;

    @Value("${app.jwt.public-key-path}")
    private String publicKeyPath;

    @Value("${app.jwt.private-key:}")
    private String privateKeyPem;

    @Value("${app.jwt.public-key:}")
    private String publicKeyPem;

    @Value("${app.jwt.access-token-expiry-ms}")
    private Long accessTokenExpiryMs;

    @Value("${app.jwt.refresh-token-expiry-ms}")
    private Long refreshTokenExpiryMs;
}