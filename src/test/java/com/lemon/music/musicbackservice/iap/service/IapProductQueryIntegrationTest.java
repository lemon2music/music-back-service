package com.lemon.music.musicbackservice.iap.service;

import com.lemon.music.musicbackservice.iap.domain.IapProductType;
import com.lemon.music.musicbackservice.iap.dto.response.IapProductResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class IapProductQueryIntegrationTest {

    @MockitoBean
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IapProductService iapProductService;

    @Test
    void seedsEightProducts() {
        List<IapProductResponse> products = iapProductService.getAvailableProducts();
        assertThat(products).hasSize(8);
        assertThat(products).anyMatch(p -> p.huaweiProductId().equals("iap_gem_card_001")
                && p.iapProductType() == IapProductType.CONSUMABLE);
        assertThat(products).anyMatch(p -> p.iapProductType() == IapProductType.AUTORENEWABLE);
        assertThat(products).anyMatch(p -> p.iapProductType() == IapProductType.NONRENEWABLE);
    }
}
