package com.ledgermate.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI at /swagger-ui.html. To call protected endpoints from it, start the backend with
 * EXPOSE_TOKEN_IN_RESPONSE=true, call POST /api/auth/login, copy "accessToken" and press Authorize.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "LedgerMate API", version = "1.0.0",
                description = "Person-by-person credit/debit ledgers with auditable reversals and corrections."),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
