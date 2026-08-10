package com.lemon.music.musicbackservice.iap.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class IapOrderEntity {
    private Long id;
    private String orderNo;
    private Long userId;
    private Long iapProductId;
    private String huaweiProductId;
    private BigDecimal amount;
    private String currency;
    private OrderStatus status;
    private String huaweiPurchaseOrderId;
    private String huaweiPurchaseToken;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private LocalDateTime fulfilledAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private LocalDateTime updatedAt;
}
