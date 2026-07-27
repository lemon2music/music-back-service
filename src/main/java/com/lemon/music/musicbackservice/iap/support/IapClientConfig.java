package com.lemon.music.musicbackservice.iap.support;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** 为 IapHuaweiClient 提供 RestClient.Builder（Spring Boot 4.1 webmvc 默认未注册该 bean）。 */
@Configuration
public class IapClientConfig {

    @Bean
    public RestClient.Builder iapRestClientBuilder() {
        return RestClient.builder();
    }
}
