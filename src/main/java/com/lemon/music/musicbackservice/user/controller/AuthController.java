package com.lemon.music.musicbackservice.user.controller;

import com.lemon.music.musicbackservice.auth.AuthContext;
import com.lemon.music.musicbackservice.auth.AuthService;
import com.lemon.music.musicbackservice.auth.RequirePermission;
import com.lemon.music.musicbackservice.common.ApiResponse;
import com.lemon.music.musicbackservice.user.dto.LoginRequest;
import com.lemon.music.musicbackservice.user.dto.LoginResponse;
import com.lemon.music.musicbackservice.user.domain.UserEntity;
import com.lemon.music.musicbackservice.user.dto.SimpleUserResponse;
import com.lemon.music.musicbackservice.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/logout")
    @RequirePermission("USER_SELF")
    public ApiResponse<Void> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String token = extractToken(authorization);
        authService.logout(token);
        return ApiResponse.ok("logout success", null);
    }

    @GetMapping("/me")
    @RequirePermission("USER_SELF")
    public ApiResponse<SimpleUserResponse> me() {
        var session = AuthContext.get();
        UserEntity user = userService.requireActiveUser(session.userId());
        return ApiResponse.ok(new SimpleUserResponse(session.userId(), session.username(), user.getMembershipLevel(), user.getPhone()));
    }

    private String extractToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring(7);
    }
}
