package com.lemon.music.musicbackservice.iap.dto.response;

import com.lemon.music.musicbackservice.iap.domain.IapProductType;
import com.lemon.music.musicbackservice.membership.domain.SubscriptionType;

import java.math.BigDecimal;

public record IapProductResponse(
        Long id,
        String name,
        String huaweiProductId,
        IapProductType iapProductType,
        BigDecimal price,
        String currency,
        SubscriptionType subscriptionType,
        String description
) {}
