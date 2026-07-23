package com.lemon.music.musicbackservice.user.dto;

public record SimpleUserResponse(Long userId,
                                 String username,
                                 String phone) {
}
