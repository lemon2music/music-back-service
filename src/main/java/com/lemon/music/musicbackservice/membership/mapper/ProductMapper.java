package com.lemon.music.musicbackservice.membership.mapper;

import com.lemon.music.musicbackservice.membership.domain.ProductEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductMapper {

    @Select("SELECT id, product_name, product_type, price, effect_config, description, status, created_at, updated_at " +
            "FROM product WHERE id = #{id}")
    ProductEntity findById(@Param("id") Long id);

    @Select("SELECT id, product_name, product_type, price, effect_config, description, status, created_at, updated_at " +
            "FROM product WHERE status = 'ACTIVE' ORDER BY id")
    List<ProductEntity> findActive();

    @Insert("INSERT INTO product (product_name, product_type, price, effect_config, description, status, created_at, updated_at) " +
            "VALUES (#{productName}, #{productType}, #{price}, #{effectConfig}, #{description}, #{status}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ProductEntity entity);
}
