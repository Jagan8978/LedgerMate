package com.ledgermate.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the "app.*" settings in application.yml (all values come from the environment). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Security security, Cors cors, Demo demo) {

    public record Security(
            String jwtSecret,
            String jwtIssuer,
            int tokenTtlMinutes,
            String cookieName,
            boolean cookieSecure,
            boolean exposeTokenInResponse) {}

    public record Cors(List<String> allowedOrigins) {}

    public record Demo(boolean enabled, String password) {}
}
