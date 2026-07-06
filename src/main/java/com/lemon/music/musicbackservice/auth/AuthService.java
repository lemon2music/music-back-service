package com.lemon.music.musicbackservice.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemon.music.musicbackservice.common.BusinessException;
import com.lemon.music.musicbackservice.user.domain.UserEntity;
import com.lemon.music.musicbackservice.user.domain.UserStatus;
import com.lemon.music.musicbackservice.user.dto.LoginRequest;
import com.lemon.music.musicbackservice.user.dto.LoginResponse;
import com.lemon.music.musicbackservice.user.dto.OnlineDurationResponse;
import com.lemon.music.musicbackservice.user.mapper.PermissionMapper;
import com.lemon.music.musicbackservice.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String TOKEN_KEY_PREFIX = "auth:token:";
    private static final String USER_TOKENS_KEY_PREFIX = "auth:user:tokens:";
    private static final String ONLINE_TOTAL_KEY_PREFIX = "auth:online:total:";

    private final UserMapper userMapper;
    private final PermissionMapper permissionMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AuthProperties authProperties;

    public LoginResponse login(LoginRequest request) {
        UserEntity user = userMapper.findByUsername(request.username());
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("username or password is incorrect");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException("username or password is incorrect");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        Set<String> permissionCodes = permissionMapper.findPermissionCodesByUserId(user.getId());
        UserSession session = new UserSession(user.getId(), user.getUsername(), Instant.now().getEpochSecond(), permissionCodes);
        String payload = toJson(session);

        long ttlHours = authProperties.tokenExpireHours() <= 0 ? 24 : authProperties.tokenExpireHours();
        Duration ttl = Duration.ofHours(ttlHours);
        String tokenKey = tokenKey(token);
        stringRedisTemplate.opsForValue().set(tokenKey, payload, ttl);

        String userTokenSetKey = userTokensKey(user.getId());
        stringRedisTemplate.opsForSet().add(userTokenSetKey, token);
        stringRedisTemplate.expire(userTokenSetKey, ttl);

        return new LoginResponse(token, user.getId(), user.getUsername(), user.getMembershipLevel(), permissionCodes);
    }

    public void logout(String token) {
        UserSession session = getSessionByToken(token);
        if (session == null) {
            return;
        }

        long now = Instant.now().getEpochSecond();
        long duration = Math.max(0, now - session.loginAtEpochSeconds());
        stringRedisTemplate.opsForValue().increment(onlineTotalKey(session.userId()), duration);

        stringRedisTemplate.delete(tokenKey(token));
        stringRedisTemplate.opsForSet().remove(userTokensKey(session.userId()), token);
    }

    public void logoutAllByUserId(Long userId) {
        String key = userTokensKey(userId);
        Set<String> tokens = stringRedisTemplate.opsForSet().members(key);
        if (tokens != null) {
            for (String token : tokens) {
                logout(token);
            }
        }
        stringRedisTemplate.delete(key);
    }

    public OnlineDurationResponse getOnlineDuration(String token) {
        UserSession session = requireSession(token);
        String totalStr = stringRedisTemplate.opsForValue().get(onlineTotalKey(session.userId()));
        long total = StringUtils.hasText(totalStr) ? Long.parseLong(totalStr) : 0L;
        long currentSession = Math.max(0, Instant.now().getEpochSecond() - session.loginAtEpochSeconds());
        return new OnlineDurationResponse(session.userId(), total + currentSession, currentSession);
    }

    public UserSession requireSession(String token) {
        UserSession session = getSessionByToken(token);
        if (session == null) {
            throw new BusinessException("login has expired, please login again");
        }
        return session;
    }

    public UserSession getSessionByToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String payload = stringRedisTemplate.opsForValue().get(tokenKey(token));
        if (!StringUtils.hasText(payload)) {
            return null;
        }
        return fromJson(payload);
    }

    private String tokenKey(String token) {
        return TOKEN_KEY_PREFIX + token;
    }

    private String userTokensKey(Long userId) {
        return USER_TOKENS_KEY_PREFIX + userId;
    }

    private String onlineTotalKey(Long userId) {
        return ONLINE_TOTAL_KEY_PREFIX + userId;
    }

    private String toJson(UserSession session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (JsonProcessingException e) {
            throw new BusinessException("serialize session failed");
        }
    }

    private UserSession fromJson(String payload) {
        try {
            return objectMapper.readValue(payload, UserSession.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException("deserialize session failed");
        }
    }
}
