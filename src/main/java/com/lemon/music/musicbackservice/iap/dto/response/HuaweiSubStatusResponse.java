package com.lemon.music.musicbackservice.iap.dto.response;

public record HuaweiSubStatusResponse(
        String responseCode,
        String responseMessage,
        String jwsSubGroupStatus
) {}
