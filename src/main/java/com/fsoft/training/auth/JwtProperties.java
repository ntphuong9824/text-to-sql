package com.fsoft.training.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** JWT settings. Fails fast at startup when the secret is missing or shorter than 32 bytes. */
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, Duration ttl) {

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set (environment variable JWT_SECRET) and be at least 32 bytes long");
        }
        if (ttl == null) {
            ttl = Duration.ofHours(24);
        }
    }

    /** Omits the secret so it can never leak through logging. */
    @Override
    public String toString() {
        return "JwtProperties[ttl=" + ttl + "]";
    }
}
