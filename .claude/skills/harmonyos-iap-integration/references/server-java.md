# 服务端 Java 完整代码参考

所有代码来自华为官方demo: https://gitcode.com/HarmonyOS_Samples/iapkit-sample-serverdemo/tree/Java

## 项目结构

```
pom.xml
src/main/java/com/huawei/iap/server/demo/
  APIExample.java           # 入口示例
  IAPServer.java            # HTTP客户端基类
  JWTGenerator.java         # JWT生成器
  JWSChecker.java           # JWS验签
  OrderService.java         # 订单服务（消耗/非消耗）
  SubscriptionService.java  # 订阅服务
  notification/
    AppServer.java            # 通知处理
    NotificationConstant.java # 通知常量
    NotificationMetaData.java # 通知元数据
    NotificationPayload.java  # 通知载荷
```

## Maven依赖 (pom.xml)

```xml
<dependencies>
    <dependency>
        <groupId>com.auth0</groupId>
        <artifactId>java-jwt</artifactId>
        <version>4.4.0</version>
    </dependency>
    <dependency>
        <groupId>commons-codec</groupId>
        <artifactId>commons-codec</artifactId>
        <version>1.9</version>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.16.2</version>
    </dependency>
</dependencies>
```

## IAPServer.java - HTTP客户端基类

```java
package com.huawei.iap.server.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class IAPServer {
    public static final String URL_ROOT = "https://iap.cloud.huawei.com";
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 5000;
    private static final String WHITE_SPACE = " ";

    public static String httpPost(String httpUrl, String jsonData) throws Exception {
        HttpURLConnection urlConnection = (HttpURLConnection) new URL(httpUrl).openConnection();
        urlConnection.setRequestMethod("POST");
        urlConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        String jwt = JWTGenerator.genJwt(jsonData);
        urlConnection.setRequestProperty("Authorization", "Bearer" + WHITE_SPACE + jwt);
        urlConnection.setDoOutput(true);
        urlConnection.setDoInput(true);
        urlConnection.setConnectTimeout(CONNECT_TIMEOUT);
        urlConnection.setReadTimeout(READ_TIMEOUT);

        try (OutputStream output = urlConnection.getOutputStream()) {
            output.write(jsonData.getBytes(StandardCharsets.UTF_8));
            output.flush();
        }

        int responseCode = urlConnection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            return readResponse(urlConnection);
        } else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            throw new Exception("jwt authentication error:" + urlConnection.getContent());
        } else {
            throw new Exception("request failed with error message: " + urlConnection.getContent());
        }
    }

    private static String readResponse(HttpURLConnection connection) throws IOException {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }
}
```

## JWTGenerator.java - JWT生成器

```java
package com.huawei.iap.server.demo;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.codec.digest.DigestUtils;

public class JWTGenerator {
    // TODO: 替换为实际的私钥文件路径
    private static final String JWT_PRI_KEY_PATH = "/path/to/key/priKey.p8";
    private static final long ACTIVE_TIME_SECOND = 3600;

    private static final Map<String, Object> JWT_HEADER = new HashMap<>();
    private static final Map<String, Object> JWT_PAYLOAD = new HashMap<>();

    static {
        JWT_HEADER.put("alg", "ES256");
        JWT_HEADER.put("typ", "JWT");
        JWT_HEADER.put("kid", "Key ID");      // TODO: 替换为实际的密钥ID

        JWT_PAYLOAD.put("iss", "Issuer ID");   // TODO: 替换为实际的密钥发行者ID
        JWT_PAYLOAD.put("aud", "iap-v1");
        JWT_PAYLOAD.put("iat", 0);
        JWT_PAYLOAD.put("exp", 0);
        JWT_PAYLOAD.put("aid", "App ID");      // TODO: 替换为实际的应用ID
        JWT_PAYLOAD.put("digest", "");
    }

    public static String genJwt(String bodyStr) throws Exception {
        try {
            Path filePath = Paths.get(JWT_PRI_KEY_PATH);
            String fileString = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            String privateKey = fileString.replace("-----BEGIN PRIVATE KEY-----", "")
                .replaceAll("\\R+", "")
                .replace("-----END PRIVATE KEY-----", "");

            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            byte[] privateKeyBytes = Base64.getDecoder().decode(privateKey);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            ECPrivateKey ecPrivateKey = (ECPrivateKey) keyFactory.generatePrivate(keySpec);

            Map<String, Object> jwtPayload = new HashMap<>(JWT_PAYLOAD);
            long signTime = System.currentTimeMillis() / Duration.ofSeconds(1).toMillis();
            String digest = DigestUtils.sha256Hex(bodyStr);
            jwtPayload.put("iat", signTime);
            jwtPayload.put("exp", signTime + ACTIVE_TIME_SECOND);
            jwtPayload.put("digest", digest);

            return JWT.create().withHeader(JWT_HEADER).withPayload(jwtPayload).sign(Algorithm.ECDSA256(ecPrivateKey));
        } catch (Exception e) {
            throw new Exception(e);
        }
    }
}
```

## JWSChecker.java - JWS验签

```java
package com.huawei.iap.server.demo;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.security.cert.*;
import java.security.interfaces.ECPublicKey;
import java.util.*;

public class JWSChecker {
    // TODO: 替换为实际的华为根CA证书路径
    private static final String CA_CERT_FILE_PATH = "/path/to/cer/RootCaG2Ecdsa.cer";
    private static final String HEADER_PARAM_X5C = "x5c";
    private static final int X5C_CHAIN_LENGTH = 3;
    private static final String HEADER_PARAM_ALG_ES256 = "ES256";
    private static final String LEAF_CERT_OID = "1.3.6.1.4.1.2011.2.415.1.1";
    private static final Boolean CRL_SOFT_FAIL_ENABLED = false;

    public static String checkAndDecodeJWS(String jwsStr) throws Exception {
        if (jwsStr == null || jwsStr.isEmpty()) {
            throw new Exception("jwsStr was null");
        }
        DecodedJWT decodedJWT = JWT.decode(jwsStr);
        if (!HEADER_PARAM_ALG_ES256.equals(decodedJWT.getAlgorithm())) {
            throw new Exception("alg must be ES256");
        }
        String[] x5cChain = decodedJWT.getHeaderClaim(HEADER_PARAM_X5C).asArray(String.class);
        if (x5cChain == null) {
            throw new Exception("x5c chain was null");
        }
        // 验证x5c证书链并获取公钥
        PublicKey publicKey = verifyChainAndGetPubKey(x5cChain);
        // 使用公钥验证JWS签名
        JWTVerifier jwtVerifier = JWT.require(Algorithm.ECDSA256((ECPublicKey) publicKey)).build();
        jwtVerifier.verify(decodedJWT);
        // 解码并返回payload
        return new String(Base64.getUrlDecoder().decode(decodedJWT.getPayload()), StandardCharsets.UTF_8);
    }

    private static PublicKey verifyChainAndGetPubKey(String[] certificates) throws Exception {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        List<Certificate> certificateList = new LinkedList<>();
        for (String certificate : certificates) {
            InputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(certificate));
            certificateList.add(certificateFactory.generateCertificate(inputStream));
        }
        if (certificateList.size() != X5C_CHAIN_LENGTH) {
            throw new Exception("invalid cert chain length");
        }

        PKIXCertPathValidatorResult certPathValidatorResult;
        try {
            PKIXParameters parameters = loadRootCAAndPKIX();
            CertPathValidator validator = CertPathValidator.getInstance("PKIX");
            parameters.setRevocationEnabled(false);
            CertPath certPath = certificateFactory.generateCertPath(
                certificateList.subList(0, X5C_CHAIN_LENGTH - 1));
            certPathValidatorResult = (PKIXCertPathValidatorResult) validator.validate(certPath, parameters);
        } catch (Exception e) {
            throw new Exception(e);
        }

        Certificate iapCert = certificateList.get(0);
        if (!(iapCert instanceof X509Certificate)) {
            throw new Exception("leaf certificate must be X509 format");
        }
        X509Certificate x509Certificate = (X509Certificate) iapCert;
        if (x509Certificate.getNonCriticalExtensionOIDs() == null ||
            !x509Certificate.getNonCriticalExtensionOIDs().contains(LEAF_CERT_OID)) {
            throw new CertPathValidatorException("OID not found");
        }
        return certPathValidatorResult.getPublicKey();
    }

    private static PKIXParameters loadRootCAAndPKIX() throws Exception {
        PKIXParameters parameters;
        try (InputStream fis = Files.newInputStream(Paths.get(CA_CERT_FILE_PATH))) {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            Certificate trustCert = certificateFactory.generateCertificate(fis);
            if (!(trustCert instanceof X509Certificate)) {
                throw new RuntimeException("root certificate must be X509 format");
            }
            Set<TrustAnchor> trustAnchors = new HashSet<>();
            trustAnchors.add(new TrustAnchor((X509Certificate) trustCert, null));
            parameters = new PKIXParameters(trustAnchors);
        }
        return parameters;
    }
}
```

## OrderService.java - 订单服务

```java
package com.huawei.iap.server.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;

public class OrderService extends IAPServer {
    private static final String URL_ORDER_STATUS_QUERY =
        "/order/harmony/v1/application/order/status/query";
    private static final String URL_ORDER_SHIPPED_CONFIRM =
        "/order/harmony/v1/application/purchase/shipped/confirm";

    /**
     * 查询消耗型或非消耗型订单的最新状态
     */
    public static void orderStatusQuery(String purchaseOrderId, String purchaseToken) throws Exception {
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("purchaseOrderId", purchaseOrderId);
        bodyMap.put("purchaseToken", purchaseToken);
        ObjectMapper objectMapper = new ObjectMapper();
        String bodyJsonStr = objectMapper.writeValueAsString(bodyMap);
        String response = httpPost(URL_ROOT + URL_ORDER_STATUS_QUERY, bodyJsonStr);
        System.out.println("order status query response is: " + response);
        // response示例 {"responseCode": "0","responseMessage": "success", "jwsPurchaseOrder": "***"
        // TODO 如果查询成功则验签解码返回数据PurchaseOrderPayload
    }

    /**
     * 确认消耗型或非消耗型商品已发货
     */
    public static void orderShippedConfirm(String purchaseOrderId, String purchaseToken) throws Exception {
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("purchaseOrderId", purchaseOrderId);
        bodyMap.put("purchaseToken", purchaseToken);
        ObjectMapper objectMapper = new ObjectMapper();
        String bodyJsonStr = objectMapper.writeValueAsString(bodyMap);
        String response = httpPost(URL_ROOT + URL_ORDER_SHIPPED_CONFIRM, bodyJsonStr);
        System.out.println("order shipped confirm response is: " + response);
    }
}
```

## SubscriptionService.java - 订阅服务

```java
package com.huawei.iap.server.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;

public class SubscriptionService extends IAPServer {
    private static final String URL_SUB_STATUS_QUERY =
        "/subscription/harmony/v1/application/subscription/status/query";
    private static final String URL_SUB_SHIPPED_CONFIRM =
        "/subscription/harmony/v1/application/purchase/shipped/confirm";

    /**
     * 查询自动续期订阅的最新状态
     */
    public static void subStatusQuery(String purchaseOrderId, String purchaseToken) throws Exception {
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("purchaseOrderId", purchaseOrderId);
        bodyMap.put("purchaseToken", purchaseToken);
        ObjectMapper objectMapper = new ObjectMapper();
        String bodyJsonStr = objectMapper.writeValueAsString(bodyMap);
        String response = httpPost(URL_ROOT + URL_SUB_STATUS_QUERY, bodyJsonStr);
        System.out.println("sub status query response is: " + response);
        // response示例 {"responseCode": "0","responseMessage": "success", "jwsSubGroupStatus": "***"
        // TODO 如果查询成功则验签解码返回数据SubGroupStatusPayload
    }

    /**
     * 确认订阅商品已发货
     */
    public static void subShippedConfirm(String purchaseOrderId, String purchaseToken) throws Exception {
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("purchaseOrderId", purchaseOrderId);
        bodyMap.put("purchaseToken", purchaseToken);
        ObjectMapper objectMapper = new ObjectMapper();
        String bodyJsonStr = objectMapper.writeValueAsString(bodyMap);
        String response = httpPost(URL_ROOT + URL_SUB_SHIPPED_CONFIRM, bodyJsonStr);
        System.out.println("sub shipped confirm response is: " + response);
    }
}
```

## 通知处理 (notification)

### NotificationConstant.java

```java
package com.huawei.iap.server.demo.notification;

public interface NotificationConstant {
    interface NotificationType {
        String DID_NEW_TRANSACTION = "DID_NEW_TRANSACTION";
        String DID_CHANGE_RENEWAL_STATUS = "DID_CHANGE_RENEWAL_STATUS";
        String REVOKE = "REVOKE";
        String RENEWAL_TIME_MODIFIED = "RENEWAL_TIME_MODIFIED";
        String EXPIRE = "EXPIRE";
    }

    interface SubNotificationType {
        String INITIAL_BUY = "INITIAL_BUY";
        String DID_RENEW = "DID_RENEW";
        String RESTORE = "RESTORE";
        String AUTO_RENEW_ENABLED = "AUTO_RENEW_ENABLED";
        String AUTO_RENEW_DISABLED = "AUTO_RENEW_DISABLED";
        String DOWNGRADE = "DOWNGRADE";
        String UPGRADE = "UPGRADE";
        String REFUND_TRANSACTION = "REFUND_TRANSACTION";
        String BILLING_RETRY = "BILLING_RETRY";
        String PRICE_INCREASE = "PRICE_INCREASE";
        String BILLING_RECOVERY = "BILLING_RECOVERY";
        String PRODUCT_NOT_FOR_SALE = "PRODUCT_NOT_FOR_SALE";
        String APPLICATION_DELETE_SUBSCRIPTION_HOSTING = "APPLICATION_DELETE_SUBSCRIPTION_HOSTING";
        String RENEWAL_EXTENDED = "RENEWAL_EXTENDED";
    }
}
```

### NotificationPayload.java

```java
package com.huawei.iap.server.demo.notification;

public class NotificationPayload {
    private String notificationType;
    private String notificationSubtype;
    private String notificationRequestId;
    private NotificationMetaData notificationMetaData;
    private String notificationVersion;
    private Long signedTime;

    // getter/setter省略，实际使用时需要完整的getter/setter
    public String getNotificationType() { return notificationType; }
    public void setNotificationType(String notificationType) { this.notificationType = notificationType; }
    public String getNotificationSubtype() { return notificationSubtype; }
    public void setNotificationSubtype(String notificationSubtype) { this.notificationSubtype = notificationSubtype; }
    public String getNotificationRequestId() { return notificationRequestId; }
    public void setNotificationRequestId(String notificationRequestId) { this.notificationRequestId = notificationRequestId; }
    public NotificationMetaData getNotificationMetaData() { return notificationMetaData; }
    public void setNotificationMetaData(NotificationMetaData notificationMetaData) { this.notificationMetaData = notificationMetaData; }
    public String getNotificationVersion() { return notificationVersion; }
    public void setNotificationVersion(String notificationVersion) { this.notificationVersion = notificationVersion; }
    public Long getSignedTime() { return signedTime; }
    public void setSignedTime(Long signedTime) { this.signedTime = signedTime; }
}
```

### NotificationMetaData.java

```java
package com.huawei.iap.server.demo.notification;

public class NotificationMetaData {
    private String environment;
    private String applicationId;
    private String packageName;
    private Integer type;
    private String currentProductId;
    private String subGroupId;
    private String subGroupGenerationId;
    private String subscriptionId;
    private String purchaseToken;
    private String purchaseOrderId;

    // getter/setter省略，实际使用时需要完整的getter/setter
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }
    public String getCurrentProductId() { return currentProductId; }
    public void setCurrentProductId(String currentProductId) { this.currentProductId = currentProductId; }
    public String getSubGroupId() { return subGroupId; }
    public void setSubGroupId(String subGroupId) { this.subGroupId = subGroupId; }
    public String getSubGroupGenerationId() { return subGroupGenerationId; }
    public void setSubGroupGenerationId(String subGroupGenerationId) { this.subGroupGenerationId = subGroupGenerationId; }
    public String getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(String subscriptionId) { this.subscriptionId = subscriptionId; }
    public String getPurchaseToken() { return purchaseToken; }
    public void setPurchaseToken(String purchaseToken) { this.purchaseToken = purchaseToken; }
    public String getPurchaseOrderId() { return purchaseOrderId; }
    public void setPurchaseOrderId(String purchaseOrderId) { this.purchaseOrderId = purchaseOrderId; }
}
```

### AppServer.java

```java
package com.huawei.iap.server.demo.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.iap.server.demo.JWSChecker;

/**
 * 服务端关键事件通知处理
 *
 */
public class AppServer {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 处理接收到的通知请求（完整端到端流程）
     *
     * @param jwsNotification 是从HTTP请求body中获取的JWS字符串
     */
    public static void handleNotificationRequest(String jwsNotification) throws Exception {
        // 步骤1: JWS验签 — 验证通知来源的真实性
        String notificationPayloadStr = JWSChecker.checkAndDecodeJWS(jwsNotification);

        // 步骤2: 解析通知载荷
        NotificationPayload notificationPayload = MAPPER.readValue(notificationPayloadStr, NotificationPayload.class);

        // 步骤3: 获取通知元数据
        NotificationMetaData metaData = notificationPayload.getNotificationMetaData();

        // 步骤4: 根据通知类型分发处理
        String notificationType = notificationPayload.getNotificationType();
        String notificationSubtype = notificationPayload.getNotificationSubtype();

        switch (notificationType) {
            case NotificationConstant.NotificationType.DID_NEW_TRANSACTION:
                // 新交易: 用户完成了一笔新的购买
                handleNewTransaction(notificationSubtype, metaData);
                break;
            case NotificationConstant.NotificationType.DID_CHANGE_RENEWAL_STATUS:
                // 续费状态变更: 用户开启/关闭了自动续费
                handleRenewalStatusChange(notificationSubtype, metaData);
                break;
            case NotificationConstant.NotificationType.REVOKE:
                // 撤销: 购买被撤销（退款等）
                handleRevoke(notificationSubtype, metaData);
                break;
            case NotificationConstant.NotificationType.RENEWAL_TIME_MODIFIED:
                // 续期时间变更
                handleRenewalTimeModified(notificationSubtype, metaData);
                break;
            case NotificationConstant.NotificationType.EXPIRE:
                // 过期: 订阅已过期
                handleExpire(notificationSubtype, metaData);
                break;
            default:
                System.out.println("Unknown notification type: " + notificationType);
                break;
        }
    }

    // ============ 以下为按notificationType维度的处理方法 ============

    private static void handleNewTransaction(String subtype, NotificationMetaData metaData) {
        // DID_NEW_TRANSACTION 可能的子类型:
        // INITIAL_BUY - 首次购买: 查询订单并发货确认
        // DID_RENEW - 续期成功: 延长用户权益
        // RESTORE - 恢复购买: 重新发放权益
        Integer productType = metaData.getType();
        System.out.println("New transaction - subtype: " + subtype
            + ", productId: " + metaData.getCurrentProductId()
            + ", orderId: " + metaData.getPurchaseOrderId());
        // TODO: 查询订单 → 验证有效性 → 发放权益 → 确认发货
    }

    private static void handleRenewalStatusChange(String subtype, NotificationMetaData metaData) {
        // DID_CHANGE_RENEWAL_STATUS 可能的子类型:
        // AUTO_RENEW_ENABLED - 用户开启了自动续费
        // AUTO_RENEW_DISABLED - 用户关闭了自动续费
        System.out.println("Renewal status changed - subtype: " + subtype
            + ", subscriptionId: " + metaData.getSubscriptionId());
        // TODO: 更新本地订阅状态记录
    }

    private static void handleRevoke(String subtype, NotificationMetaData metaData) {
        // REVOKE 可能的子类型:
        // REFUND_TRANSACTION - 退款撤销
        System.out.println("Purchase revoked - subtype: " + subtype
            + ", orderId: " + metaData.getPurchaseOrderId());
        // TODO: 撤回用户权益
    }

    private static void handleRenewalTimeModified(String subtype, NotificationMetaData metaData) {
        // RENEWAL_TIME_MODIFIED 可能的子类型:
        // RENEWAL_EXTENDED - 续期延长
        System.out.println("Renewal time modified - subtype: " + subtype
            + ", subscriptionId: " + metaData.getSubscriptionId());
        // TODO: 更新订阅到期时间
    }

    private static void handleExpire(String subtype, NotificationMetaData metaData) {
        // EXPIRE 可能的子类型:
        // BILLING_RETRY - 扣费重试中
        // BILLING_RECOVERY - 扣费恢复
        // PRODUCT_NOT_FOR_SALE - 商品下架
        System.out.println("Subscription expired - subtype: " + subtype
            + ", subscriptionId: " + metaData.getSubscriptionId());
        // TODO: 标记订阅过期，按需处理权益
    }

    private static void handleSync(String subtype, NotificationMetaData metaData) {
        System.out.println("Sync event - subtype: " + subtype);
        // TODO: 同步本地数据与IAP服务器状态
    }
}
```

## 使用示例 (APIExample.java)

```java
package com.huawei.iap.server.demo;

import com.huawei.iap.server.demo.notification.AppServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;

public class APIExample {
    public static void main(String[] args) throws Exception {
        // 1. 生成JWT
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("purchaseOrderId", "purchaseOrderId");
        bodyMap.put("purchaseToken", "purchaseToken");
        String bodyJsonStr = new ObjectMapper().writeValueAsString(bodyMap);
        String jwt = JWTGenerator.genJwt(bodyJsonStr);

        // 2. 查询订单状态
        OrderService.orderStatusQuery("purchaseOrderId", "purchaseToken");

        // 3. 确认订单发货
        OrderService.orderShippedConfirm("purchaseOrderId", "purchaseToken");

        // 4. 查询订阅状态
        SubscriptionService.subStatusQuery("purchaseOrderId", "purchaseToken");

        // 5. 确认订阅发货
        SubscriptionService.subShippedConfirm("purchaseOrderId", "purchaseToken");

        // 6. JWS验签
        String payload = JWSChecker.checkAndDecodeJWS("jwsStr");

        // 7. 处理通知（完整流程：JWS验签 + 业务分发）
        // 实际场景中 jwsNotification 是从HTTP请求body中获取的JWS字符串
        String jwsNotification = "eyJ..."; // 华为IAP服务器发送的JWS字符串
        AppServer.handleNotificationRequest(jwsNotification);
    }
}
```
