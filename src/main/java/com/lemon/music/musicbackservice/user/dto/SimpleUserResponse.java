package com.lemon.music.musicbackservice.user.dto;

import com.lemon.music.musicbackservice.user.domain.MembershipLevel;

public record SimpleUserResponse(Long userId,
                                 String username,
                                 MembershipLevel membershipLevel) {
}
