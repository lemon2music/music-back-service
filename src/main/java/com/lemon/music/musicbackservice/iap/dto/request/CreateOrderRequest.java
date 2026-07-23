package com.lemon.music.musicbackservice.iap.dto.request;

import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(
        @NotNull(message = "IAP 商品ID不能为空")
        Long iapProductId
) {}
