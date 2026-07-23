package com.lemon.music.musicbackservice.iap.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IapFulfillmentEntity {
    private Long id;
    private String huaweiPurchaseOrderId;
    private String huaweiPurchaseToken;
    private Long iapOrderId;
    private Long userId;
    private Long iapProductId;
    private IapProductType iapProductType;
    private FulfillmentAction action;
    private Integer pointsGranted;
    private String effectSnapshot;
    private LocalDateTime createdAt;
}
