package com.ledgermate.security;

import com.ledgermate.config.AppProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Builds the HttpOnly, SameSite=Strict cookie that carries the JWT (not readable by scripts). */
@Component
public class AuthCookieFactory {

    private final AppProperties.Security cfg;
    private final JwtService jwt;

    public AuthCookieFactory(AppProperties props, JwtService jwt) {
        this.cfg = props.security();
        this.jwt = jwt;
    }

    public String name() {
        return cfg.cookieName();
    }

    public ResponseCookie create(String token) {
        return base(token, jwt.ttl());
    }

    public ResponseCookie clear() {
        return base("", Duration.ZERO);
    }

    private ResponseCookie base(String value, Duration maxAge) {
        return ResponseCookie.from(cfg.cookieName(), value)
                .httpOnly(true)
                .secure(cfg.cookieSecure())
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
