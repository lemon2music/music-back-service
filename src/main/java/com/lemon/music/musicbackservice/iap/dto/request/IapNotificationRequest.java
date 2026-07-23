package com.lemon.music.musicbackservice.iap.dto.request;

import jakarta.validation.constraints.NotBlank;

public record IapNotificationRequest(
        @NotBlank(message = "jwsNotification 不能为空")
        String jwsNotification
) {}
