package com.lemon.music.musicbackservice.user.mapper;

import com.lemon.music.musicbackservice.user.domain.RoleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoleMapper {

    @Select("""
            <script>
            SELECT id, role_code, role_name
            FROM app_role
            WHERE role_code IN
            <foreach item='roleCode' collection='roleCodes' open='(' separator=',' close=')'>
                #{roleCode}
            </foreach>
            </script>
            """)
    List<RoleEntity> findByRoleCodes(@Param("roleCodes") List<String> roleCodes);

    @Select("""
            SELECT r.id, r.role_code, r.role_name
            FROM app_role r
            JOIN user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
            """)
    List<RoleEntity> findRolesByUserId(@Param("userId") Long userId);

    @Select("SELECT id, role_code, role_name FROM app_role WHERE role_code = #{roleCode} LIMIT 1")
    RoleEntity findByRoleCode(@Param("roleCode") String roleCode);
}
