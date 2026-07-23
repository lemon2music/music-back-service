package com.lemon.music.musicbackservice.user.service;

import com.lemon.music.musicbackservice.common.BusinessException;
import com.lemon.music.musicbackservice.membership.service.MembershipService;
import com.lemon.music.musicbackservice.user.domain.RoleEntity;
import com.lemon.music.musicbackservice.user.domain.UserEntity;
import com.lemon.music.musicbackservice.user.domain.UserStatus;
import com.lemon.music.musicbackservice.user.dto.AssignRolesRequest;
import com.lemon.music.musicbackservice.user.dto.RegisterRequest;
import com.lemon.music.musicbackservice.user.mapper.RoleMapper;
import com.lemon.music.musicbackservice.user.mapper.UserMapper;
import com.lemon.music.musicbackservice.user.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String DEFAULT_ROLE_CODE = "USER";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final MembershipService membershipService;

    @Transactional
    public Long register(RegisterRequest request) {
        UserEntity existing = userMapper.findByUsername(request.username());
        if (existing != null) {
            throw new BusinessException("username already exists");
        }

        if (request.phone() != null) {
            UserEntity phoneOwner = userMapper.findByPhone(request.phone());
            if (phoneOwner != null) {
                throw new BusinessException("phone already exists");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        UserEntity entity = new UserEntity();
        entity.setUsername(request.username());
        entity.setPasswordHash(passwordEncoder.encode(request.password()));
        entity.setStatus(UserStatus.ACTIVE);
        entity.setPhone(request.phone());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        userMapper.insert(entity);

        RoleEntity defaultRole = roleMapper.findByRoleCode(DEFAULT_ROLE_CODE);
        if (defaultRole == null) {
            throw new BusinessException("default role USER not found");
        }
        userRoleMapper.insert(entity.getId(), defaultRole.getId());

        // 初始化新用户会员信息（VIP1、0 积分、无会员资格）
        membershipService.initializeMembership(entity.getId());

        return entity.getId();
    }

    @Transactional
    public void cancelUser(Long userId) {
        requireActiveUser(userId);
        if (userMapper.deactivateById(userId, LocalDateTime.now()) <= 0) {
            throw new BusinessException("cancel user failed");
        }
    }

    @Transactional
    public void assignRoles(Long userId, AssignRolesRequest request) {
        requireActiveUser(userId);
        List<RoleEntity> roles = roleMapper.findByRoleCodes(request.roleCodes().stream().toList());
        if (roles.size() != request.roleCodes().size()) {
            throw new BusinessException("some roles do not exist");
        }

        userRoleMapper.deleteByUserId(userId);
        for (RoleEntity role : roles) {
            userRoleMapper.insert(userId, role.getId());
        }
    }

    public UserEntity requireActiveUser(Long userId) {
        UserEntity user = userMapper.findById(userId);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("user does not exist or has been deactivated");
        }
        return user;
    }
}
