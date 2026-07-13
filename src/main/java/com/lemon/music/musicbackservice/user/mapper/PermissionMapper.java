package com.lemon.music.musicbackservice.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Set;

@Mapper
public interface PermissionMapper {

    @Select("""
            SELECT DISTINCT p.permission_code
            FROM app_permission p
            JOIN role_permission rp ON rp.permission_id = p.id
            JOIN user_role ur ON ur.role_id = rp.role_id
            WHERE ur.user_id = #{userId}
            """)
    Set<String> findPermissionCodesByUserId(@Param("userId") Long userId);
}
