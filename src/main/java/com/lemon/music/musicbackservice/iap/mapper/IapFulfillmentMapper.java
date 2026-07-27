package com.lemon.music.musicbackservice.iap.mapper;

import com.lemon.music.musicbackservice.iap.domain.FulfillmentAction;
import com.lemon.music.musicbackservice.iap.domain.IapFulfillmentEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface IapFulfillmentMapper {

    @Insert("INSERT INTO iap_fulfillment (huawei_purchase_order_id, huawei_purchase_token, iap_order_id, "
            + "user_id, iap_product_id, iap_product_type, action, points_granted, effect_snapshot, created_at) "
            + "VALUES (#{huaweiPurchaseOrderId}, #{huaweiPurchaseToken}, #{iapOrderId}, #{userId}, "
            + "#{iapProductId}, #{iapProductType}, #{action}, #{pointsGranted}, #{effectSnapshot}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(IapFulfillmentEntity entity);

    @Select("SELECT id, huawei_purchase_order_id, huawei_purchase_token, iap_order_id, user_id, iap_product_id, "
            + "iap_product_type, action, points_granted, effect_snapshot, created_at "
            + "FROM iap_fulfillment WHERE huawei_purchase_order_id = #{poId} AND action = #{action}")
    IapFulfillmentEntity findByPurchaseOrderIdAndAction(@Param("poId") String purchaseOrderId,
                                                       @Param("action") FulfillmentAction action);
}
