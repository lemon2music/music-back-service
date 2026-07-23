package com.lemon.music.musicbackservice.iap.dto.response;

public record ReportPurchaseResponse(
        boolean fulfilled,
        Long orderId,
        String message
) {}
