package com.lemon.music.musicbackservice.membership.dto.request;

import com.lemon.music.musicbackservice.membership.domain.SubscriptionType;
import jakarta.validation.constraints.NotNull;

public record PurchaseSubscriptionRequest(
        @NotNull(message = "订阅类型不能为空")
        SubscriptionType subscriptionType
) {
}
