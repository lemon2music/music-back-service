package com.lemon.music.musicbackservice.iap.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IapNotificationLogEntity {
    private Long id;
    private String notificationRequestId;
    private String notificationType;
    private String notificationSubtype;
    private String huaweiPurchaseOrderId;
    private String huaweiPurchaseToken;
    private String huaweiProductId;
    private Long userId;
    private String rawJws;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
}
