package com.lemon.music.musicbackservice.user.domain;

import lombok.Data;

@Data
public class PermissionEntity {

    private Long id;
    private String permissionCode;
    private String description;
}
