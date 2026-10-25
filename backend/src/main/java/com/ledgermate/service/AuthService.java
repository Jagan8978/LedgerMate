package com.ledgermate.service;

import com.ledgermate.dto.AuthDtos.LoginRequest;
import com.ledgermate.dto.AuthDtos.RegisterRequest;
import com.ledgermate.dto.AuthDtos.UpdateProfileRequest;
import com.ledgermate.dto.AuthDtos.UserResponse;
import com.ledgermate.entity.User;
import com.ledgermate.exception.BusinessRuleException;
import com.ledgermate.exception.ConflictException;
import com.ledgermate.exception.NotFoundException;
import com.ledgermate.repository.UserRepository;
import com.ledgermate.security.AuthenticatedUser;
import com.ledgermate.util.Text;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.Locale;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    /** Compared against when the email is unknown so response time does not reveal accounts. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    @Transactional
    public User register(RegisterRequest req) {
        String email = normalizeEmail(req.email());
        if (req.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessRuleException("Password is too long (maximum 72 bytes).");
        }
        if (users.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists.");
        }
        User user = new User(email, Text.clean(req.displayName()), encoder.encode(req.password()));
        return users.save(user);
    }

    @Transactional(readOnly = true)
    public User login(LoginRequest req) {
        User user = users.findByEmail(normalizeEmail(req.email())).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean ok = encoder.matches(req.password(), hash);
        if (user == null || !ok) {
            throw new BadCredentialsException("Invalid email or password.");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public UserResponse me(AuthenticatedUser principal) {
        return toResponse(load(principal));
    }

    @Transactional
    public UserResponse updateProfile(AuthenticatedUser principal, UpdateProfileRequest req) {
        User user = load(principal);
        if (req.displayName() != null) {
            user.setDisplayName(Text.clean(req.displayName()));
        }
        if (req.timezone() != null) {
            String tz = req.timezone().trim();
            if (!ZoneId.getAvailableZoneIds().contains(tz)) {
                throw new BusinessRuleException("Unknown timezone.");
            }
            user.setTimezone(tz);
        }
        return toResponse(users.save(user));
    }

    public static UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getDisplayName(), u.getTimezone());
    }

    private User load(AuthenticatedUser principal) {
        return users.findById(principal.id()).orElseThrow(() -> new NotFoundException("User not found."));
    }

    private static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
