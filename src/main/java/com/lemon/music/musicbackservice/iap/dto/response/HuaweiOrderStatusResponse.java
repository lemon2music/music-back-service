package com.lemon.music.musicbackservice.iap.dto.response;

public record HuaweiOrderStatusResponse(
        String responseCode,
        String responseMessage,
        String jwsPurchaseOrder
) {}
