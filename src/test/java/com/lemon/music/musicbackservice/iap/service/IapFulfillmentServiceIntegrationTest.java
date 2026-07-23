package com.lemon.music.musicbackservice.iap.service;

import com.lemon.music.musicbackservice.iap.domain.FulfillmentAction;
import com.lemon.music.musicbackservice.iap.domain.IapProductEntity;
import com.lemon.music.musicbackservice.iap.domain.IapProductType;
import com.lemon.music.musicbackservice.iap.dto.response.PreOrderResponse;
import com.lemon.music.musicbackservice.iap.dto.response.ReportPurchaseResponse;
import com.lemon.music.musicbackservice.iap.mapper.IapFulfillmentMapper;
import com.lemon.music.musicbackservice.iap.mapper.IapProductMapper;
import com.lemon.music.musicbackservice.iap.support.JwsVerifier;
import com.lemon.music.musicbackservice.membership.domain.MembershipEntity;
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
class IapFulfillmentServiceIntegrationTest {

    @MockitoBean
    StringRedisTemplate stringRedisTemplate;
    @MockitoBean
    JwsVerifier jwsVerifier;

    @Autowired
    private IapOrderService iapOrderService;
    @Autowired
    private IapFulfillmentService iapFulfillmentService;
    @Autowired
    private IapProductMapper iapProductMapper;
    @Autowired
    private IapFulfillmentMapper fulfillmentMapper;
    @Autowired
    private UserService userService;
    @Autowired
    private MembershipMapper membershipMapper;

    @Test
    void reportGrantsPointsAndIsIdempotent() throws Exception {
        Long userId = userService.register(new RegisterRequest("ful1", "pw", null));
        IapProductEntity gem = iapProductMapper.findByHuaweiProductId("iap_gem_card_001");
        PreOrderResponse pre = iapOrderService.createOrder(userId, gem.getId());

        String payloadJson = "{\"productId\":\"iap_gem_card_001\",\"purchaseOrderId\":\"PO_A\","
                + "\"purchaseToken\":\"T_A\",\"developerPayload\":\"" + pre.orderNo() + "\","
                + "\"finishStatus\":\"2\",\"purchaseOrderRevocationReasonCode\":null,\"productType\":0}";
        when(jwsVerifier.checkAndDecode(anyString())).thenReturn(payloadJson);
        String purchaseData = "{\"type\":0,\"jwsPurchaseOrder\":\"dummy\"}";

        ReportPurchaseResponse r1 = iapFulfillmentService.handleReport(userId, IapProductType.CONSUMABLE, purchaseData);

        assertThat(r1.fulfilled()).isTrue();
        MembershipEntity m = membershipMapper.findByUserId(userId);
        assertThat(m.getCurrentPoints()).isEqualTo(1000); // 宝石加速卡 1000 积分
        assertThat(fulfillmentMapper.findByPurchaseOrderIdAndAction("PO_A", FulfillmentAction.GRANT)).isNotNull();

        // 重复上报：幂等，不再发放
        ReportPurchaseResponse r2 = iapFulfillmentService.handleReport(userId, IapProductType.CONSUMABLE, purchaseData);
        assertThat(r2.fulfilled()).isFalse();
        assertThat(membershipMapper.findByUserId(userId).getCurrentPoints()).isEqualTo(1000);
    }

    @Test
    void reportRevokedOrderDoesNotGrant() throws Exception {
        Long userId = userService.register(new RegisterRequest("ful2", "pw", null));
        IapProductEntity gem = iapProductMapper.findByHuaweiProductId("iap_gem_card_001");
        PreOrderResponse pre = iapOrderService.createOrder(userId, gem.getId());

        String payloadJson = "{\"productId\":\"iap_gem_card_001\",\"purchaseOrderId\":\"PO_B\","
                + "\"purchaseToken\":\"T_B\",\"developerPayload\":\"" + pre.orderNo() + "\","
                + "\"finishStatus\":\"2\",\"purchaseOrderRevocationReasonCode\":\"1\",\"productType\":0}";
        when(jwsVerifier.checkAndDecode(anyString())).thenReturn(payloadJson);

        ReportPurchaseResponse r = iapFulfillmentService.handleReport(userId, IapProductType.CONSUMABLE,
                "{\"type\":0,\"jwsPurchaseOrder\":\"dummy\"}");

        assertThat(r.fulfilled()).isFalse();
        assertThat(membershipMapper.findByUserId(userId).getCurrentPoints()).isZero();
    }
}
