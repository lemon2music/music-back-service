package com.lemon.music.musicbackservice.membership.mapper;

import com.lemon.music.musicbackservice.membership.domain.MembershipPointsHistoryEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MembershipPointsHistoryMapper {

    @Insert("INSERT INTO membership_points_history " +
            "(user_id, points_change, points_before, points_after, change_reason, description, created_at) " +
            "VALUES (#{userId}, #{pointsChange}, #{pointsBefore}, #{pointsAfter}, " +
            "#{changeReason}, #{description}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MembershipPointsHistoryEntity entity);

    @Select("SELECT id, user_id, points_change, points_before, points_after, change_reason, description, created_at " +
            "FROM membership_points_history WHERE user_id = #{userId} " +
            "ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<MembershipPointsHistoryEntity> findByUserIdWithPaging(@Param("userId") Long userId,
                                                                @Param("offset") int offset,
                                                                @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM membership_points_history WHERE user_id = #{userId}")
    int countByUserId(@Param("userId") Long userId);
}
