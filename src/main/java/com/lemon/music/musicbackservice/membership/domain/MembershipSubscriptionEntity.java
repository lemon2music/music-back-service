package com.lemon.music.musicbackservice.membership.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员订阅购买记录。
 */
@Data
public class MembershipSubscriptionEntity {

    private Long id;
    private Long userId;
    private SubscriptionType subscriptionType;
    private LocalDateTime startTime;
    private LocalDateTime expireTime;
    private LocalDateTime createdAt;
}
