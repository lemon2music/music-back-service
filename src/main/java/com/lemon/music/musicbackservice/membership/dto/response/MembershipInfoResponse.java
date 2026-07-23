package com.lemon.music.musicbackservice.membership.dto.response;

import com.lemon.music.musicbackservice.membership.domain.MembershipType;
import com.lemon.music.musicbackservice.membership.domain.VipLevel;

import java.time.LocalDateTime;

public record MembershipInfoResponse(
        Long userId,
        Integer currentPoints,
        MembershipType membershipType,
        VipLevel vipLevel,
        Boolean hasMembership,
        LocalDateTime subscriptionExpireAt,
        /** 下一等级提示信息 */
        String nextLevelInfo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
