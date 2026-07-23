package com.lemon.music.musicbackservice.membership.mapper;

import com.lemon.music.musicbackservice.membership.domain.MembershipEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Insert;

import java.util.List;

@Mapper
public interface MembershipMapper {

    @Insert("INSERT INTO membership (user_id, current_points, membership_type, vip_level, " +
            "has_membership, subscription_expire_at, created_at, updated_at) " +
            "VALUES (#{userId}, #{currentPoints}, #{membershipType}, #{vipLevel}, " +
            "#{hasMembership}, #{subscriptionExpireAt}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MembershipEntity entity);

    @Select("SELECT id, user_id, current_points, membership_type, vip_level, " +
            "has_membership, subscription_expire_at, created_at, updated_at " +
            "FROM membership WHERE user_id = #{userId}")
    MembershipEntity findByUserId(@Param("userId") Long userId);

    @Select("SELECT id, user_id, current_points, membership_type, vip_level, " +
            "has_membership, subscription_expire_at, created_at, updated_at " +
            "FROM membership WHERE id = #{id}")
    MembershipEntity findById(@Param("id") Long id);

    @Update("UPDATE membership SET current_points = #{currentPoints}, " +
            "membership_type = #{membershipType}, vip_level = #{vipLevel}, " +
            "has_membership = #{hasMembership}, subscription_expire_at = #{subscriptionExpireAt}, " +
            "updated_at = #{updatedAt} WHERE id = #{id}")
    int update(MembershipEntity entity);

    @Select("SELECT id, user_id, current_points, membership_type, vip_level, " +
            "has_membership, subscription_expire_at, created_at, updated_at " +
            "FROM membership")
    List<MembershipEntity> findAll();
}
