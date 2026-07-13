package com.lemon.music.musicbackservice.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(long tokenExpireHours) {
}
