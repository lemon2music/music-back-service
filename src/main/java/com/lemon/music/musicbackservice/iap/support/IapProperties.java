package com.lemon.music.musicbackservice.iap.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** IAP Kit 对接配置。密钥类字段经环境变量注入，不入库。 */
@ConfigurationProperties(prefix = "app.iap")
public record IapProperties(
        String baseUrl,
        String rootCaCertPath,
        Jwt jwt
) {
    /** 调用华为 REST 所需的 ES256 JWT 凭证。 */
    public record Jwt(String privateKeyPath, String keyId, String issuerId, String appId) {}
}
