package com.aiobservability.services.authservice.service;

import com.aiobservability.services.authservice.domain.Role;
import com.aiobservability.services.authservice.domain.UserAccount;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserAccountService {
    private final Map<String, UserAccount> usersByUsername = new ConcurrentHashMap<>();

    public UserAccountService(PasswordEncoder passwordEncoder) {
        seedUser("admin", "admin123", Role.ADMIN, passwordEncoder);
        seedUser("engineer", "engineer123", Role.ENGINEER, passwordEncoder);
        seedUser("viewer", "viewer123", Role.VIEWER, passwordEncoder);
    }

    public UserAccount findByUsername(String username) {
        return usersByUsername.get(username);
    }

    private void seedUser(String username, String rawPassword, Role role, PasswordEncoder passwordEncoder) {
        UserAccount user = new UserAccount(
                UUID.randomUUID(),
                username,
                passwordEncoder.encode(rawPassword),
                role,
                true,
                Instant.now()
        );
        usersByUsername.put(username, user);
    }
}
