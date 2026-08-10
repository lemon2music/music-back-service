package com.lemon.music.musicbackservice.iap.dto.payload;

import java.util.List;

public record SubscriptionStatus(
        String subGroupGenerationId,
        String subscriptionId,
        String purchaseToken,
        String status,
        Long expiresTime,
        PurchaseOrderPayload lastPurchaseOrder,
        SubRenewalInfo renewalInfo,
        List<PurchaseOrderPayload> recentPurchaseOrderList
) {}
