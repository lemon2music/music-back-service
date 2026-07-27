package com.lemon.music.musicbackservice.user.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserEntity {

    private Long id;
    private String username;
    private String passwordHash;
    private UserStatus status;
    private String phone;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
