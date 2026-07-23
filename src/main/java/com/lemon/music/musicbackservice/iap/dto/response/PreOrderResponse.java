package com.lemon.music.musicbackservice.iap.dto.response;

import com.lemon.music.musicbackservice.iap.domain.IapProductType;

import java.math.BigDecimal;

public record PreOrderResponse(
        String orderNo,
        String huaweiProductId,
        IapProductType iapProductType,
        BigDecimal amount,
        String currency
) {}
