package com.lemon.music.musicbackservice.membership.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员信息（每个用户一条）。
 */
@Data
public class MembershipEntity {

    private Long id;
    private Long userId;
    private Integer currentPoints;
    private MembershipType membershipType;
    private VipLevel vipLevel;
    private Boolean hasMembership;
    private LocalDateTime subscriptionExpireAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
