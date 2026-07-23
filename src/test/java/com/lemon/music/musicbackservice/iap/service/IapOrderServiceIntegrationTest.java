package com.lemon.music.musicbackservice.iap.service;

import com.lemon.music.musicbackservice.iap.domain.IapProductEntity;
import com.lemon.music.musicbackservice.iap.domain.IapProductType;
import com.lemon.music.musicbackservice.iap.dto.response.PreOrderResponse;
import com.lemon.music.musicbackservice.iap.mapper.IapProductMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class IapOrderServiceIntegrationTest {

    @MockitoBean
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IapOrderService iapOrderService;
    @Autowired
    private IapProductMapper iapProductMapper;

    @Test
    void createOrderReturnsOrderNoAndHuaweiProductId() {
        IapProductEntity gem = iapProductMapper.findByHuaweiProductId("iap_gem_card_001");

        PreOrderResponse resp = iapOrderService.createOrder(1001L, gem.getId());

        assertThat(resp.orderNo()).startsWith("IAP");
        assertThat(resp.huaweiProductId()).isEqualTo("iap_gem_card_001");
        assertThat(resp.iapProductType()).isEqualTo(IapProductType.CONSUMABLE);
    }
}
