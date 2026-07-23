package com.lemon.music.musicbackservice.iap.support;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** 生成调用华为 IAP REST 所需的 ES256 JWT（Authorization: Bearer <jwt>）。 */
@Slf4j
@Component
public class IapJwtGenerator {

    private static final long ACTIVE_TIME_SECOND = 3600L;

    private final IapProperties.Jwt jwt;
    private final ECPrivateKey privateKey;

    public IapJwtGenerator(IapProperties properties) {
        this.jwt = properties.jwt();
        this.privateKey = loadPrivateKey(properties.jwt().privateKeyPath());
    }

    public String generate(String bodyJson) {
        if (privateKey == null) {
            throw new IllegalStateException("IAP 私钥未配置（app.iap.jwt.private-key-path）");
        }
        long now = System.currentTimeMillis() / 1000L;
        Map<String, Object> payload = new HashMap<>();
        payload.put("iss", jwt.issuerId());
        payload.put("aud", "iap-v1");
        payload.put("iat", now);
        payload.put("exp", now + ACTIVE_TIME_SECOND);
        payload.put("aid", jwt.appId());
        payload.put("digest", DigestUtils.sha256Hex(bodyJson));

        Map<String, Object> header = new HashMap<>();
        header.put("alg", "ES256");
        header.put("typ", "JWT");
        header.put("kid", jwt.keyId());

        return JWT.create().withHeader(header).withPayload(payload).sign(Algorithm.ECDSA256(privateKey));
    }

    private static ECPrivateKey loadPrivateKey(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            String content;
            if (path.startsWith("classpath:")) {
                content = new String(
                        IapJwtGenerator.class.getClassLoader()
                                .getResourceAsStream(path.substring("classpath:".length())).readAllBytes(),
                        StandardCharsets.UTF_8);
            } else {
                content = Files.readString(Paths.get(path));
            }
            String pem = content.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replaceAll("\\R+", "")
                    .replace("-----END PRIVATE KEY-----", "");
            byte[] der = Base64.getDecoder().decode(pem);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
            return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(spec);
        } catch (Exception e) {
            log.warn("加载 IAP 私钥失败（部署前须配置 app.iap.jwt.private-key-path）: {}", path);
            return null;
        }
    }
}
