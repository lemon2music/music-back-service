package com.lemon.music.musicbackservice.iap;

import com.lemon.music.musicbackservice.iap.support.IapProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class IapInfrastructureTest {

    @MockitoBean
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IapProperties iapProperties;

    @Test
    void iapPropertiesBound() {
        assertThat(iapProperties.baseUrl()).isEqualTo("https://iap.test");
        assertThat(iapProperties.jwt().keyId()).isEqualTo("test-kid");
    }
}
