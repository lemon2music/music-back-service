package com.lemon.music.musicbackservice.iap.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemon.music.musicbackservice.iap.dto.response.HuaweiOrderStatusResponse;
import com.lemon.music.musicbackservice.iap.support.IapJwtGenerator;
import com.lemon.music.musicbackservice.iap.support.IapProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class IapHuaweiClientTest {

    @Test
    void orderStatusQueryPostsJwtAndParsesJws() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        IapJwtGenerator jwtGen = org.mockito.Mockito.mock(IapJwtGenerator.class);
        org.mockito.Mockito.when(jwtGen.generate(org.mockito.ArgumentMatchers.anyString())).thenReturn("test-jwt");
        IapProperties props = new IapProperties("https://iap.test", "/cert",
                new IapProperties.Jwt("/key", "kid", "iss", "aid"));
        IapHuaweiClient client = new IapHuaweiClient(builder, props, jwtGen, new ObjectMapper());

        server.expect(requestTo("https://iap.test/order/harmony/v1/application/order/status/query"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-jwt"))
                .andRespond(withSuccess(
                        "{\"responseCode\":\"0\",\"responseMessage\":\"ok\",\"jwsPurchaseOrder\":\"JWS_PO\"}",
                        MediaType.APPLICATION_JSON));

        HuaweiOrderStatusResponse resp = client.orderStatusQuery("PO1", "T1");

        assertThat(resp.responseCode()).isEqualTo("0");
        assertThat(resp.jwsPurchaseOrder()).isEqualTo("JWS_PO");
        server.verify();
    }
}
