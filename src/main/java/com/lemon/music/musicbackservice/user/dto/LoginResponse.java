package com.lemon.music.musicbackservice.user.dto;

import java.util.Set;

public record LoginResponse(String token,
                            Long userId,
                            String username,
                            Set<String> permissions) {
}
