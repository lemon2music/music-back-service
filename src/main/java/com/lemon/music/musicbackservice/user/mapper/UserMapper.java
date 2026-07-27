package com.lemon.music.musicbackservice.user.mapper;

import com.lemon.music.musicbackservice.user.domain.UserEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface UserMapper {

    @Insert("""
            INSERT INTO app_user(username, password_hash, membership_level, status, phone, created_at, updated_at)
            VALUES(#{username}, #{passwordHash}, 'NORMAL', #{status}, #{phone}, #{createdAt}, #{updatedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserEntity user);

    @Select("SELECT id, username, password_hash, status, phone, created_at, updated_at FROM app_user WHERE username = #{username} LIMIT 1")
    UserEntity findByUsername(@Param("username") String username);

    @Select("SELECT id, username, password_hash, status, phone, created_at, updated_at FROM app_user WHERE id = #{id} LIMIT 1")
    UserEntity findById(@Param("id") Long id);

    @Select("SELECT id, username, password_hash, status, phone, created_at, updated_at FROM app_user WHERE phone = #{phone} LIMIT 1")
    UserEntity findByPhone(@Param("phone") String phone);

    @Update("UPDATE app_user SET status = 'DEACTIVATED', updated_at = #{updatedAt} WHERE id = #{id}")
    int deactivateById(@Param("id") Long id, @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 对用户行加悲观锁（SELECT ... FOR UPDATE），用于串行化同一用户的并发写操作。
     * 必须在事务内调用，锁持有至事务提交。
     */
    @Select("SELECT id FROM app_user WHERE id = #{id} FOR UPDATE")
    Long lockById(@Param("id") Long id);
}
