package com.lemon.music.musicbackservice.membership.mapper;

import com.lemon.music.musicbackservice.membership.domain.UserProductEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserProductMapper {

    @Insert("INSERT INTO user_product (user_id, product_id, purchase_time, used, used_time) " +
            "VALUES (#{userId}, #{productId}, #{purchaseTime}, #{used}, #{usedTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserProductEntity entity);

    @Select("SELECT id, user_id, product_id, purchase_time, used, used_time " +
            "FROM user_product WHERE id = #{id}")
    UserProductEntity findById(@Param("id") Long id);

    @Select("SELECT id, user_id, product_id, purchase_time, used, used_time " +
            "FROM user_product WHERE user_id = #{userId} AND product_id = #{productId}")
    List<UserProductEntity> findByUserIdAndProductId(@Param("userId") Long userId,
                                                      @Param("productId") Long productId);

    @Update("UPDATE user_product SET used = #{used}, used_time = #{usedTime} WHERE id = #{id}")
    int update(UserProductEntity entity);
}
