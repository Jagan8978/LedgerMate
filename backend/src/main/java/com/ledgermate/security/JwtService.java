package com.ledgermate.security;

import com.ledgermate.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** Issues and validates signed, expiring JWTs (HS256). The secret comes from the environment. */
@Service
public class JwtService {

    private final SecretKey key;
    private final String issuer;
    private final Duration ttl;

    public JwtService(AppProperties props) {
        AppProperties.Security cfg = props.security();
        byte[] bytes;
        try {
            bytes = Decoders.BASE64.decode(cfg.jwtSecret() == null ? "" : cfg.jwtSecret());
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "JWT_SECRET must be a Base64 string of at least 32 random bytes. See README step 'Create your .env file'.");
        }
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET is too short: it must decode to at least 32 bytes. See README step 'Create your .env file'.");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.issuer = cfg.jwtIssuer();
        this.ttl = Duration.ofMinutes(cfg.tokenTtlMinutes());
    }

    public String issue(UUID userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /** Returns the user when signature, issuer and expiry are all valid; otherwise empty. */
    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new AuthenticatedUser(UUID.fromString(claims.getSubject()),
                    claims.get("email", String.class)));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public Duration ttl() {
        return ttl;
    }
}
