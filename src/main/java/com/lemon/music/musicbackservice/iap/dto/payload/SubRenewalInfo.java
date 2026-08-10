package com.lemon.music.musicbackservice.iap.dto.payload;

public record SubRenewalInfo(String productId,
                             String environment,
                             String subGroupGenerationId,
                             String nextRenewPeriodProductId,
                             String autoRenewStatusCode,
                             Boolean hasInBillingRetryPeriod,
                             String priceIncreaseStatusCode,
                             String offerTypeCode,
                             String offerId,
                             Long renewalPrice,
                             String currency,
                             Long renewalTime,
                             String expirationIntent,
                             String nextRenewPeriodPayload) {}
