package com.lemon.music.musicbackservice.iap.mapper;

import com.lemon.music.musicbackservice.iap.domain.IapOrderEntity;
import com.lemon.music.musicbackservice.iap.domain.OrderStatus;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface IapOrderMapper {

    @Insert("INSERT INTO iap_order (order_no, user_id, iap_product_id, huawei_product_id, amount, currency, "
            + "status, huawei_purchase_order_id, huawei_purchase_token, created_at, paid_at, fulfilled_at, updated_at) "
            + "VALUES (#{orderNo}, #{userId}, #{iapProductId}, #{huaweiProductId}, #{amount}, #{currency}, "
            + "#{status}, #{huaweiPurchaseOrderId}, #{huaweiPurchaseToken}, #{createdAt}, #{paidAt}, #{fulfilledAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(IapOrderEntity entity);

    @Select("SELECT id, order_no, user_id, iap_product_id, huawei_product_id, amount, currency, status, "
            + "huawei_purchase_order_id, huawei_purchase_token, created_at, paid_at, fulfilled_at, updated_at "
            + "FROM iap_order WHERE order_no = #{orderNo}")
    IapOrderEntity findByOrderNo(@Param("orderNo") String orderNo);

    @Update("UPDATE iap_order SET huawei_purchase_order_id = #{purchaseOrderId}, "
            + "huawei_purchase_token = #{purchaseToken}, status = #{status}, "
            + "paid_at = #{paidAt}, fulfilled_at = #{fulfilledAt}, updated_at = #{updatedAt} "
            + "WHERE id = #{id}")
    int updateFulfillment(@Param("id") Long id,
                          @Param("purchaseOrderId") String purchaseOrderId,
                          @Param("purchaseToken") String purchaseToken,
                          @Param("status") OrderStatus status,
                          @Param("paidAt") LocalDateTime paidAt,
                          @Param("fulfilledAt") LocalDateTime fulfilledAt,
                          @Param("updatedAt") LocalDateTime updatedAt);
}
