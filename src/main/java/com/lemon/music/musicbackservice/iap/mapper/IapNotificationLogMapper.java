package com.lemon.music.musicbackservice.iap.mapper;

import com.lemon.music.musicbackservice.iap.domain.IapNotificationLogEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IapNotificationLogMapper {

    @Insert("INSERT INTO iap_notification_log (notification_request_id, notification_type, notification_subtype, "
            + "huawei_purchase_order_id, huawei_purchase_token, huawei_product_id, user_id, raw_jws, status, "
            + "error_message, created_at) "
            + "VALUES (#{notificationRequestId}, #{notificationType}, #{notificationSubtype}, "
            + "#{huaweiPurchaseOrderId}, #{huaweiPurchaseToken}, #{huaweiProductId}, #{userId}, #{rawJws}, "
            + "#{status}, #{errorMessage}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(IapNotificationLogEntity entity);

    @Select("SELECT id, notification_request_id, notification_type, notification_subtype, "
            + "huawei_purchase_order_id, huawei_purchase_token, huawei_product_id, user_id, raw_jws, status, "
            + "error_message, created_at FROM iap_notification_log WHERE notification_request_id = #{requestId}")
    IapNotificationLogEntity findByRequestId(@Param("requestId") String requestId);

    @Update("UPDATE iap_notification_log SET status = #{status}, error_message = #{errorMessage} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("errorMessage") String errorMessage);
}
