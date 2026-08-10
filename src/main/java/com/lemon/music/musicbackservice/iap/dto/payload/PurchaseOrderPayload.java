package com.lemon.music.musicbackservice.iap.dto.payload;

/** jwsPurchaseOrder 验签解码后的订单载荷。 */
public record PurchaseOrderPayload(
        String applicationId,
        String productId,
        Integer productType,
        String purchaseOrderId,
        String purchaseToken,
        Long purchaseTime,
        Long signedTime,
        String countryCode,
        Double price,
        String currency,
        String environment,
        String finishStatus,
        Boolean needFinish,
        String developerPayload,
        String purchaseOrderRevocationReasonCode,
        String offerId,
        String duration,
        String durationTypeCode,
        String subGroupId,
        String subscriptionId,
        String subGroupGenerationId
) {}
