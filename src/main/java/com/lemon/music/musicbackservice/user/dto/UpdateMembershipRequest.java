package com.lemon.music.musicbackservice.user.dto;

import com.lemon.music.musicbackservice.user.domain.MembershipLevel;
import jakarta.validation.constraints.NotNull;

public record UpdateMembershipRequest(
        @NotNull(message = "membershipLevel is required")
        MembershipLevel membershipLevel
) {
}
