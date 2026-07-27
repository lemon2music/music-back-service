package com.lemon.music.musicbackservice.membership.mapper;

import com.lemon.music.musicbackservice.membership.domain.MembershipSubscriptionEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MembershipSubscriptionMapper {

    @Insert("INSERT INTO membership_subscription " +
            "(user_id, subscription_type, start_time, expire_time, created_at) " +
            "VALUES (#{userId}, #{subscriptionType}, #{startTime}, #{expireTime}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MembershipSubscriptionEntity entity);

    @Select("SELECT id, user_id, subscription_type, start_time, expire_time, created_at " +
            "FROM membership_subscription WHERE user_id = #{userId} ORDER BY start_time DESC")
    List<MembershipSubscriptionEntity> findByUserId(@Param("userId") Long userId);
}
