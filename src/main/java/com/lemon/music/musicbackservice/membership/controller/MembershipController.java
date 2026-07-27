package com.lemon.music.musicbackservice.membership.controller;

import com.lemon.music.musicbackservice.auth.AuthContext;
import com.lemon.music.musicbackservice.common.ApiResponse;
import com.lemon.music.musicbackservice.membership.domain.MembershipEntity;
import com.lemon.music.musicbackservice.membership.domain.MembershipPointsHistoryEntity;
import com.lemon.music.musicbackservice.membership.dto.response.MembershipInfoResponse;
import com.lemon.music.musicbackservice.membership.dto.response.PointsHistoryResponse;
import com.lemon.music.musicbackservice.membership.service.MembershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 会员系统 API。购买流程已迁移至 /api/iap/*。 */
@RestController
@RequestMapping("/api/membership")
@RequiredArgsConstructor
public class MembershipController {

    private final MembershipService membershipService;

    @GetMapping("/info")
    public ApiResponse<MembershipInfoResponse> getMembershipInfo() {
        Long userId = AuthContext.get().userId();
        MembershipEntity membership = membershipService.getMembershipByUserId(userId);
        String nextLevelInfo = membershipService.calculateNextLevelInfo(
                membership.getCurrentPoints(), membership.getVipLevel());
        MembershipInfoResponse response = new MembershipInfoResponse(
                membership.getUserId(), membership.getCurrentPoints(), membership.getMembershipType(),
                membership.getVipLevel(), membership.getHasMembership(), membership.getSubscriptionExpireAt(),
                nextLevelInfo, membership.getCreatedAt(), membership.getUpdatedAt());
        return ApiResponse.ok(response);
    }

    @GetMapping("/points-history")
    public ApiResponse<List<PointsHistoryResponse>> getPointsHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = AuthContext.get().userId();
        return ApiResponse.ok(membershipService.getPointsHistory(userId, page, size).stream()
                .map(h -> new PointsHistoryResponse(h.getId(), h.getPointsChange(), h.getPointsBefore(),
                        h.getPointsAfter(), h.getChangeReason(), h.getDescription(), h.getCreatedAt()))
                .toList());
    }
}
