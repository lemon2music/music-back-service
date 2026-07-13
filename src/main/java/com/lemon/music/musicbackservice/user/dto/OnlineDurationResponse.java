package com.lemon.music.musicbackservice.user.dto;

public record OnlineDurationResponse(Long userId,
                                     long totalOnlineSeconds,
                                     long currentSessionSeconds) {
}
