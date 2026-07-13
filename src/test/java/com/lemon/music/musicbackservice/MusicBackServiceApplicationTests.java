package com.lemon.music.musicbackservice;

import com.lemon.music.musicbackservice.common.BusinessException;
import com.lemon.music.musicbackservice.user.domain.UserEntity;
import com.lemon.music.musicbackservice.user.dto.RegisterRequest;
import com.lemon.music.musicbackservice.user.mapper.UserMapper;
import com.lemon.music.musicbackservice.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class MusicBackServiceApplicationTests {

    @MockitoBean
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Test
    void contextLoads() {
    }

    @Transactional
    @Test
    void registerStoresPhoneAndRejectsDuplicate() {
        Long id = userService.register(new RegisterRequest("alice", "password1", "13800138000"));
        UserEntity saved = userMapper.findById(id);
        assertThat(saved.getPhone()).isEqualTo("13800138000");

        assertThatThrownBy(() -> userService.register(new RegisterRequest("bob", "password2", "13800138000")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("phone already exists");
    }

    @Transactional
    @Test
    void underscoreToCamelCaseMappingWorks() {
        // 测试 MyBatis 的下划线到驼峰映射是否正常工作
        Long id = userService.register(new RegisterRequest("charlie", "password3", "13900139000"));
        UserEntity user = userMapper.findById(id);

        // 验证所有 snake_case 字段都正确映射到了 camelCase
        assertThat(user).isNotNull();
        assertThat(user.getId()).isNotNull();
        assertThat(user.getUsername()).isEqualTo("charlie");
        assertThat(user.getPasswordHash()).isNotNull(); // password_hash -> passwordHash
        assertThat(user.getMembershipLevel()).isNotNull(); // membership_level -> membershipLevel
        assertThat(user.getStatus()).isNotNull(); // status -> status
        assertThat(user.getPhone()).isEqualTo("13900139000"); // phone -> phone
        assertThat(user.getCreatedAt()).isNotNull(); // created_at -> createdAt
        assertThat(user.getUpdatedAt()).isNotNull(); // updated_at -> updatedAt
    }
}
