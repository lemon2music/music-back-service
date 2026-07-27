package com.lemon.music.musicbackservice.iap.domain;

import com.lemon.music.musicbackservice.membership.domain.SubscriptionType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class IapProductEntity {
    private Long id;
    private InternalProductType internalType;
    private Long internalRefId;
    private SubscriptionType subscriptionType;
    private String huaweiProductId;
    private IapProductType iapProductType;
    private String name;
    private BigDecimal price;
    private String currency;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
