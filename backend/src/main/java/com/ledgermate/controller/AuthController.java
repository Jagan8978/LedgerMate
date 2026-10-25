package com.ledgermate.controller;

import com.ledgermate.config.AppProperties;
import com.ledgermate.dto.AuthDtos.AuthResponse;
import com.ledgermate.dto.AuthDtos.LoginRequest;
import com.ledgermate.dto.AuthDtos.RegisterRequest;
import com.ledgermate.dto.AuthDtos.UpdateProfileRequest;
import com.ledgermate.dto.AuthDtos.UserResponse;
import com.ledgermate.entity.User;
import com.ledgermate.security.AuthCookieFactory;
import com.ledgermate.security.AuthenticatedUser;
import com.ledgermate.security.JwtService;
import com.ledgermate.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final JwtService jwt;
    private final AuthCookieFactory cookies;
    private final boolean exposeToken;

    public AuthController(AuthService auth, JwtService jwt, AuthCookieFactory cookies, AppProperties props) {
        this.auth = auth;
        this.jwt = jwt;
        this.cookies = cookies;
        this.exposeToken = props.security().exposeTokenInResponse();
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return issue(auth.register(request), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return issue(auth.login(request), HttpStatus.OK);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .build();
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return auth.me(user);
    }

    @PatchMapping("/me")
    public UserResponse updateProfile(@AuthenticationPrincipal AuthenticatedUser user,
                                      @Valid @RequestBody UpdateProfileRequest request) {
        return auth.updateProfile(user, request);
    }

    private ResponseEntity<AuthResponse> issue(User user, HttpStatus status) {
        String token = jwt.issue(user.getId(), user.getEmail());
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookies.create(token).toString())
                .body(new AuthResponse(AuthService.toResponse(user), exposeToken ? token : null));
    }
}
