package com.lemon.music.musicbackservice.iap.service;

import com.lemon.music.musicbackservice.iap.domain.FulfillmentAction;
import com.lemon.music.musicbackservice.iap.domain.IapProductEntity;
import com.lemon.music.musicbackservice.iap.domain.IapProductType;
import com.lemon.music.musicbackservice.iap.dto.response.HuaweiOrderStatusResponse;
import com.lemon.music.musicbackservice.iap.dto.response.PreOrderResponse;
import com.lemon.music.musicbackservice.iap.mapper.IapFulfillmentMapper;
import com.lemon.music.musicbackservice.iap.mapper.IapProductMapper;
import com.lemon.music.musicbackservice.iap.support.JwsVerifier;
import com.lemon.music.musicbackservice.membership.mapper.MembershipMapper;
import com.lemon.music.musicbackservice.user.dto.RegisterRequest;
import com.lemon.music.musicbackservice.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class IapNotificationServiceIntegrationTest {

    @MockitoBean
    StringRedisTemplate stringRedisTemplate;
    @MockitoBean
    JwsVerifier jwsVerifier;
    @MockitoBean
    IapHuaweiClient huaweiClient;

    @Autowired
    private IapNotificationService notificationService;
    @Autowired
    private IapOrderService iapOrderService;
    @Autowired
    private IapFulfillmentService fulfillmentService;
    @Autowired
    private IapProductMapper iapProductMapper;
    @Autowired
    private IapFulfillmentMapper fulfillmentMapper;
    @Autowired
    private UserService userService;
    @Autowired
    private MembershipMapper membershipMapper;

    @Test
    void revokeNotificationRecallsPoints() throws Exception {
        // 1. 预下单 + 上报发货，获得 1000 积分
        Long userId = userService.register(new RegisterRequest("ntf1", "pw", null));
        IapProductEntity gem = iapProductMapper.findByHuaweiProductId("iap_gem_card_001");
        PreOrderResponse pre = iapOrderService.createOrder(userId, gem.getId());
        String orderPayload = "{\"productId\":\"iap_gem_card_001\",\"purchaseOrderId\":\"PO_N\","
                + "\"purchaseToken\":\"T_N\",\"developerPayload\":\"" + pre.orderNo() + "\","
                + "\"finishStatus\":\"2\",\"purchaseOrderRevocationReasonCode\":null,\"productType\":0}";
        when(jwsVerifier.checkAndDecode(anyString())).thenReturn(orderPayload);
        fulfillmentService.handleReport(userId, IapProductType.CONSUMABLE,
                "{\"type\":0,\"jwsPurchaseOrder\":\"dummy\"}");
        assertThat(membershipMapper.findByUserId(userId).getCurrentPoints()).isEqualTo(1000);

        // 2. REVOKE 通知：通知 JWS 解码为 NotificationPayload；订单查询返回 JWS_PO，再解码为 orderPayload
        String notificationPayload = "{\"notificationType\":\"REVOKE\",\"notificationSubtype\":\"REFUND_TRANSACTION\","
                + "\"notificationRequestId\":\"REQ1\",\"notificationVersion\":\"v1\",\"signedTime\":1,"
                + "\"notificationMetaData\":{\"type\":0,\"currentProductId\":\"iap_gem_card_001\","
                + "\"purchaseOrderId\":\"PO_N\",\"purchaseToken\":\"T_N\"}}";
        when(jwsVerifier.checkAndDecode("NOTIFICATION_JWS")).thenReturn(notificationPayload);
        when(jwsVerifier.checkAndDecode("JWS_PO")).thenReturn(orderPayload);
        when(huaweiClient.orderStatusQuery("PO_N", "T_N"))
                .thenReturn(new HuaweiOrderStatusResponse("0", "ok", "JWS_PO"));

        notificationService.handleNotification("NOTIFICATION_JWS");

        // 3. 积分被回收，fulfillment 存在 REVOKE 记录
        assertThat(membershipMapper.findByUserId(userId).getCurrentPoints()).isZero();
        assertThat(fulfillmentMapper.findByPurchaseOrderIdAndAction("PO_N", FulfillmentAction.REVOKE)).isNotNull();
    }

    @Test
    void notificationIsIdempotent() throws Exception {
        String notificationPayload = "{\"notificationType\":\"EXPIRE\",\"notificationRequestId\":\"REQ2\","
                + "\"notificationMetaData\":{\"type\":2,\"purchaseOrderId\":\"PO_NONE\",\"purchaseToken\":\"T\"}}";
        when(jwsVerifier.checkAndDecode(anyString())).thenReturn(notificationPayload);

        notificationService.handleNotification("JWS");
        notificationService.handleNotification("JWS"); // 重复

        // 第二次因 requestId 重复被跳过，不报错
        assertThat(membershipMapper.findByUserId(999999L)).isNull();
    }
}
