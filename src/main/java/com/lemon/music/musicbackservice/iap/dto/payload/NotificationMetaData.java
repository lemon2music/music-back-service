package com.lemon.music.musicbackservice.iap.dto.payload;

public record NotificationMetaData(
        String environment,
        String applicationId,
        String packageName,
        Integer type,
        String currentProductId,
        String subGroupId,
        String subGroupGenerationId,
        String subscriptionId,
        String purchaseToken,
        String purchaseOrderId
) {}
