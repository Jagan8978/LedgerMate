package com.ledgermate.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Enter a valid email address")
            @Size(max = 254, message = "Email is too long")
            String email,
            @NotBlank(message = "Password is required")
            @Size(min = 10, max = 72, message = "Password must be 10 to 72 characters")
            String password,
            @Size(max = 100, message = "Name is too long")
            String displayName) {}

    public record LoginRequest(
            @NotBlank(message = "Email is required") String email,
            @NotBlank(message = "Password is required") String password) {}

    public record UpdateProfileRequest(
            @Size(max = 100, message = "Name is too long") String displayName,
            @Size(max = 64, message = "Invalid timezone") String timezone) {}

    public record UserResponse(UUID id, String email, String displayName, String timezone) {}

    /** accessToken is only filled when app.security.expose-token-in-response=true (API testing). */
    public record AuthResponse(UserResponse user, String accessToken) {}
}
