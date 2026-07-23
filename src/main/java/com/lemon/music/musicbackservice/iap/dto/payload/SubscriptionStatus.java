package com.lemon.music.musicbackservice.iap.dto.payload;

public record SubscriptionStatus(
        String subGroupGenerationId,
        String subscriptionId,
        String purchaseToken,
        String status,
        Long expiresTime,
        PurchaseOrderPayload lastPurchaseOrder,
        SubRenewalInfo renewalInfo
) {}
