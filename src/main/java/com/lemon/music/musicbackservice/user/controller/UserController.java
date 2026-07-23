package com.lemon.music.musicbackservice.user.controller;

import com.lemon.music.musicbackservice.auth.AuthContext;
import com.lemon.music.musicbackservice.auth.AuthService;
import com.lemon.music.musicbackservice.auth.RequirePermission;
import com.lemon.music.musicbackservice.common.ApiResponse;
import com.lemon.music.musicbackservice.user.dto.AssignRolesRequest;
import com.lemon.music.musicbackservice.user.dto.OnlineDurationResponse;
import com.lemon.music.musicbackservice.user.dto.RegisterRequest;
import com.lemon.music.musicbackservice.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<Map<String, Long>> register(@Valid @RequestBody RegisterRequest request) {
        Long userId = userService.register(request);
        return ApiResponse.ok("register success", Map.of("userId", userId));
    }

    @PostMapping("/cancel")
    @RequirePermission("USER_SELF")
    public ApiResponse<Void> cancelCurrentUser(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        Long userId = AuthContext.get().userId();
        userService.cancelUser(userId);
        authService.logoutAllByUserId(userId);
        return ApiResponse.ok("cancel user success", null);
    }

    @GetMapping("/me/online-duration")
    @RequirePermission("USER_SELF")
    public ApiResponse<OnlineDurationResponse> onlineDuration(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String token = extractToken(authorization);
        return ApiResponse.ok(authService.getOnlineDuration(token));
    }

    @PutMapping("/{userId}/roles")
    @RequirePermission("USER_ASSIGN_ROLE")
    public ApiResponse<Void> assignRoles(@PathVariable("userId") Long userId,
                                         @Valid @RequestBody AssignRolesRequest request) {
        userService.assignRoles(userId, request);
        return ApiResponse.ok("assign roles success", null);
    }

    private String extractToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring(7);
    }
}
