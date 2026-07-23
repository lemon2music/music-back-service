package com.lemon.music.musicbackservice.iap.mapper;

import com.lemon.music.musicbackservice.iap.domain.IapProductEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface IapProductMapper {

    @Select("SELECT id, internal_type, internal_ref_id, subscription_type, huawei_product_id, "
            + "iap_product_type, name, price, currency, status, created_at, updated_at "
            + "FROM iap_product WHERE id = #{id}")
    IapProductEntity findById(@Param("id") Long id);

    @Select("SELECT id, internal_type, internal_ref_id, subscription_type, huawei_product_id, "
            + "iap_product_type, name, price, currency, status, created_at, updated_at "
            + "FROM iap_product WHERE status = 'ACTIVE' ORDER BY id")
    List<IapProductEntity> findActive();

    @Select("SELECT id, internal_type, internal_ref_id, subscription_type, huawei_product_id, "
            + "iap_product_type, name, price, currency, status, created_at, updated_at "
            + "FROM iap_product WHERE huawei_product_id = #{huaweiProductId}")
    IapProductEntity findByHuaweiProductId(@Param("huaweiProductId") String huaweiProductId);
}
