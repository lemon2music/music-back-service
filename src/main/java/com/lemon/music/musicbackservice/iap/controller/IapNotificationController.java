package com.lemon.music.musicbackservice.iap.controller;

import com.lemon.music.musicbackservice.common.ApiResponse;
import com.lemon.music.musicbackservice.iap.dto.request.IapNotificationRequest;
import com.lemon.music.musicbackservice.iap.service.IapNotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 华为关键事件通知回调（公开，路径已在 AuthInterceptor 白名单）。 */
@RestController
@RequestMapping("/api/iap")
@RequiredArgsConstructor
public class IapNotificationController {

    private final IapNotificationService iapNotificationService;

    @PostMapping("/notifications")
    public ApiResponse<Void> notify(@Valid @RequestBody IapNotificationRequest request) {
        iapNotificationService.handleNotification(request.jwsNotification());
        return ApiResponse.ok("ok", null);
    }
}
