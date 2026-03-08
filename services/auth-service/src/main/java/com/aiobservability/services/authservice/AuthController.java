package com.aiobservability.services.authservice;

import com.aiobservability.services.authservice.api.LoginRequest;
import com.aiobservability.services.authservice.api.LogoutRequest;
import com.aiobservability.services.authservice.api.RefreshRequest;
import com.aiobservability.services.authservice.api.UserProfileResponse;
import com.aiobservability.services.authservice.service.AuthPayload;
import com.aiobservability.services.authservice.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        AuthPayload payload = authService.login(request.username(), request.password());
        return ResponseEntity.ok(Map.of(
                "tokens", payload.tokens(),
                "user", payload.user()
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthPayload payload = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(Map.of(
                "tokens", payload.tokens(),
                "user", payload.user()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest request, Authentication authentication) {
        String username = authentication == null ? null : authentication.getName();
        String refreshToken = request == null ? null : request.refreshToken();
        authService.logout(refreshToken, username);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me(Authentication authentication) {
        return ResponseEntity.ok(authService.me(authentication.getName()));
    }
}
