package com.lemon.music.musicbackservice.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "username cannot be blank")
        @Size(min = 3, max = 32, message = "username length must be 3-32")
        String username,

        @NotBlank(message = "password cannot be blank")
        @Size(min = 6, max = 64, message = "password length must be 6-64")
        String password
) {
}
