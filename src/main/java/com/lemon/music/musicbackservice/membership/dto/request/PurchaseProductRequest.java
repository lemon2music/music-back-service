package com.lemon.music.musicbackservice.membership.dto.request;

import jakarta.validation.constraints.NotNull;

public record PurchaseProductRequest(
        @NotNull(message = "商品ID不能为空")
        Long productId
) {
}
