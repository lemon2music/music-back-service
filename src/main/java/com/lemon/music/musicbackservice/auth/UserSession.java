package com.lemon.music.musicbackservice.auth;

import java.util.Set;

public record UserSession(Long userId,
                          String username,
                          long loginAtEpochSeconds,
                          Set<String> permissions) {
}
