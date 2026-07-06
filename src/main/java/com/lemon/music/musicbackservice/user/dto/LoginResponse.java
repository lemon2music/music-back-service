package com.lemon.music.musicbackservice.user.dto;

import com.lemon.music.musicbackservice.user.domain.MembershipLevel;

import java.util.Set;

public record LoginResponse(String token,
                            Long userId,
                            String username,
                            MembershipLevel membershipLevel,
                            Set<String> permissions) {
}
