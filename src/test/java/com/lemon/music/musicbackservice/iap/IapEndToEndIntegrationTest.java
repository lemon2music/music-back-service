package com.lemon.music.musicbackservice.iap;

import com.lemon.music.musicbackservice.iap.domain.FulfillmentAction;
import com.lemon.music.musicbackservice.iap.domain.IapProductEntity;
import com.lemon.music.musicbackservice.iap.domain.IapProductType;
import com.lemon.music.musicbackservice.iap.dto.response.HuaweiOrderStatusResponse;
import com.lemon.music.musicbackservice.iap.dto.response.PreOrderResponse;
import com.lemon.music.musicbackservice.iap.dto.response.ReportPurchaseResponse;
import com.lemon.music.musicbackservice.iap.mapper.IapFulfillmentMapper;
import com.lemon.music.musicbackservice.iap.mapper.IapProductMapper;
import com.lemon.music.musicbackservice.iap.service.IapFulfillmentService;
import com.lemon.music.musicbackservice.iap.service.IapHuaweiClient;
import com.lemon.music.musicbackservice.iap.service.IapNotificationService;
import com.lemon.music.musicbackservice.iap.service.IapOrderService;
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
class IapEndToEndIntegrationTest {

    @MockitoBean
    StringRedisTemplate stringRedisTemplate;
    @MockitoBean
    JwsVerifier jwsVerifier;
    @MockitoBean
    IapHuaweiClient huaweiClient;

    @Autowired
    private IapOrderService orderService;
    @Autowired
    private IapFulfillmentService fulfillmentService;
    @Autowired
    private IapNotificationService notificationService;
    @Autowired
    private IapProductMapper productMapper;
    @Autowired
    private IapFulfillmentMapper fulfillmentMapper;
    @Autowired
    private UserService userService;
    @Autowired
    private MembershipMapper membershipMapper;

    @Test
    void preorderReportGrantThenRevokeRecalls() throws Exception {
        Long userId = userService.register(new RegisterRequest("e2e", "pw", null));
        IapProductEntity gem = productMapper.findByHuaweiProductId("iap_gem_card_001");

        // 预下单
        PreOrderResponse pre = orderService.createOrder(userId, gem.getId());
        assertThat(pre.huaweiProductId()).isEqualTo("iap_gem_card_001");

        // 上报发货
        String orderPayload = "{\"productId\":\"iap_gem_card_001\",\"purchaseOrderId\":\"PO_E2E\","
                + "\"purchaseToken\":\"T_E2E\",\"developerPayload\":\"" + pre.orderNo() + "\","
                + "\"finishStatus\":\"2\",\"purchaseOrderRevocationReasonCode\":null,\"productType\":0}";
        when(jwsVerifier.checkAndDecode(anyString())).thenReturn(orderPayload);
        ReportPurchaseResponse report = fulfillmentService.handleReport(userId, IapProductType.CONSUMABLE,
                "{\"type\":0,\"jwsPurchaseOrder\":\"dummy\"}");
        assertThat(report.fulfilled()).isTrue();
        assertThat(membershipMapper.findByUserId(userId).getCurrentPoints()).isEqualTo(1000);

        // REVOKE 通知回收
        String notificationPayload = "{\"notificationType\":\"REVOKE\",\"notificationSubtype\":\"REFUND_TRANSACTION\","
                + "\"notificationRequestId\":\"REQ_E2E\",\"notificationVersion\":\"v1\",\"signedTime\":1,"
                + "\"notificationMetaData\":{\"type\":0,\"currentProductId\":\"iap_gem_card_001\","
                + "\"purchaseOrderId\":\"PO_E2E\",\"purchaseToken\":\"T_E2E\"}}";
        when(jwsVerifier.checkAndDecode("NOTIF")).thenReturn(notificationPayload);
        when(jwsVerifier.checkAndDecode("JWS_PO")).thenReturn(orderPayload);
        when(huaweiClient.orderStatusQuery("PO_E2E", "T_E2E"))
                .thenReturn(new HuaweiOrderStatusResponse("0", "ok", "JWS_PO"));

        notificationService.handleNotification("NOTIF");

        assertThat(membershipMapper.findByUserId(userId).getCurrentPoints()).isZero();
        assertThat(fulfillmentMapper.findByPurchaseOrderIdAndAction("PO_E2E", FulfillmentAction.GRANT)).isNotNull();
        assertThat(fulfillmentMapper.findByPurchaseOrderIdAndAction("PO_E2E", FulfillmentAction.REVOKE)).isNotNull();
    }
}
