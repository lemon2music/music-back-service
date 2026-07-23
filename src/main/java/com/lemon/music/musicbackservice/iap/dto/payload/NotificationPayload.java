package com.lemon.music.musicbackservice.iap.dto.payload;

/** 通知 JWS 验签解码后的载荷。 */
public record NotificationPayload(
        String notificationType,
        String notificationSubtype,
        String notificationRequestId,
        NotificationMetaData notificationMetaData,
        String notificationVersion,
        Long signedTime
) {}
