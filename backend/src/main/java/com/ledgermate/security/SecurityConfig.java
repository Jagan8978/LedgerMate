package com.ledgermate.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgermate.config.AppProperties;
import com.ledgermate.dto.ApiError;
import com.ledgermate.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                            UserRepository users, AppProperties props,
                                            ObjectMapper mapper) throws Exception {
        AppProperties.Security sec = props.security();

        // Double-submit CSRF cookie for the browser app: JS reads XSRF-TOKEN and echoes it
        // in the X-XSRF-TOKEN header. SameSite=Strict adds a second layer.
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepo.setCookieCustomizer(c -> c.sameSite("Strict").secure(sec.cookieSecure()));

        // CSRF only matters when the browser attaches credentials by itself, i.e. the auth cookie.
        // Requests without that cookie are exempt: Bearer-token API clients (browsers never add an
        // Authorization header to a cross-site request on their own) and anonymous callers, who
        // then get a clean 401 from the authorization rules instead of a confusing 403.
        String authCookie = sec.cookieName();
        RequestMatcher noAmbientCredentials = request -> {
            jakarta.servlet.http.Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (jakarta.servlet.http.Cookie c : cookies) {
                    if (authCookie.equals(c.getName())) {
                        return false;
                    }
                }
            }
            return true;
        };

        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepo)
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers("/api/auth/login", "/api/auth/register")
                .ignoringRequestMatchers(noAmbientCredentials))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((request, response, ex) -> writeError(mapper, request.getRequestURI(),
                        response, HttpStatus.UNAUTHORIZED, "Authentication is required."))
                .accessDeniedHandler((request, response, ex) -> {
                    // The CSRF filter runs before authentication. A request that carries no
                    // credentials at all is simply unauthenticated (401); a request that does
                    // carry a session cookie but a bad CSRF token is forbidden (403).
                    boolean hasCredentials = hasCredentials(request, sec.cookieName());
                    if (hasCredentials) {
                        writeError(mapper, request.getRequestURI(), response, HttpStatus.FORBIDDEN,
                                "Request was rejected (missing or invalid CSRF token).");
                    } else {
                        writeError(mapper, request.getRequestURI(), response, HttpStatus.UNAUTHORIZED,
                                "Authentication is required.");
                    }
                }))
            .addFilterBefore(new JwtAuthenticationFilter(jwtService, users, sec.cookieName()),
                    UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(props.cors().allowedOrigins());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-XSRF-TOKEN", "Idempotency-Key"));
        cfg.setExposedHeaders(List.of("Content-Disposition"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }

    private static boolean hasCredentials(jakarta.servlet.http.HttpServletRequest request, String cookieName) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return true;
        }
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie c : cookies) {
                if (cookieName.equals(c.getName()) && c.getValue() != null && !c.getValue().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void writeError(ObjectMapper mapper, String path, HttpServletResponse response,
                                   HttpStatus status, String message) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(),
                new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, path, List.of()));
    }
}
