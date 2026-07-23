package com.lemon.music.musicbackservice.membership.dto.response;

import com.lemon.music.musicbackservice.membership.domain.ProductType;

import java.math.BigDecimal;

public record ProductInfoResponse(
        Long id,
        String productName,
        ProductType productType,
        BigDecimal price,
        String description,
        Boolean purchasable
) {
}
