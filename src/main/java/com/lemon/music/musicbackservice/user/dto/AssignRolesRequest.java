package com.lemon.music.musicbackservice.user.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record AssignRolesRequest(
        @NotEmpty(message = "roleCodes cannot be empty")
        Set<String> roleCodes
) {
}
