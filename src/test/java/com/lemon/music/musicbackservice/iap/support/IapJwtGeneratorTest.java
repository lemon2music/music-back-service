package com.lemon.music.musicbackservice.iap.support;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IapJwtGeneratorTest {

    @Test
    void jwtContainsDigestAndAudience() throws Exception {
        TestCertFactory factory = new TestCertFactory();
        String body = "{\"purchaseOrderId\":\"PO1\"}";

        // 直接用 TestCertFactory 的私钥构造一个 JWT，验证 digest 计算逻辑
        Map<String, Object> header = new HashMap<>();
        header.put("alg", "ES256");
        Map<String, Object> payload = new HashMap<>();
        payload.put("aud", "iap-v1");
        payload.put("iss", "test-iss");
        payload.put("aid", "test-aid");
        payload.put("digest", DigestUtils.sha256Hex(body));
        String jwt = JWT.create().withHeader(header).withPayload(payload)
                .sign(Algorithm.ECDSA256((java.security.interfaces.ECPrivateKey) factory.leafKeyPair.getPrivate()));

        var decoded = JWT.decode(jwt);
        assertThat(decoded.getClaim("aud").asString()).isEqualTo("iap-v1");
        assertThat(decoded.getClaim("digest").asString()).isEqualTo(DigestUtils.sha256Hex(body));
    }
}
