# HarmonyOS IAP Kit 服务端对接 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `music-back-service` 新增 `iap` 模块，接入 HarmonyOS IAP Kit，覆盖消耗型/非消耗型/自动续期订阅/非续期订阅四种商品的购买、补单、关键事件通知（含退款回收）服务端流程。

**Architecture:** 新增独立 `iap` 领域模块（domain/dto/mapper/service/controller/support），作为"支付凭证 → 业务权益"的翻译层，通过调用 `MembershipService` 发放/回收权益。新增 4 张表（`iap_product`/`iap_order`/`iap_fulfillment`/`iap_notification_log`），靠唯一约束做幂等。删除旧的"直接发放"购买接口，全部购买走 IAP 真实支付。

**Tech Stack:** Spring Boot 4.1.0（webmvc）、MyBatis 3.0.4（注解 mapper）、MySQL(运行时)/H2(测试)、java-jwt 4.4.0、commons-codec、BouncyCastle(测试)、Lombok、Jakarta Validation。

## Global Constraints

- Java 21；包根 `com.lemon.music.musicbackservice`。
- DTO/值对象/枚举/配置一律用 `record`；DB 实体用 Lombok `@Data`。依赖经 `@RequiredArgsConstructor` 构造器注入。
- mapper 扫描需在 `MusicBackServiceApplication` 与 `config/MyBatisConfig` **两处** `@MapperScan` 同步声明 `com.lemon.music.musicbackservice.iap.mapper`。
- 所有 controller 方法返回 `common/ApiResponse<T>`；业务失败抛 `common/BusinessException`（→ 400）。
- MyBatis `map-underscore-to-camel-case: true`；枚举按**名称**存储（默认 `EnumTypeHandler`）。
- SQL 必须 H2(MySQL 模式) 兼容：`CREATE TABLE IF NOT EXISTS`、`DATETIME`、`AUTO_INCREMENT`、`DECIMAL`、`BOOLEAN`，禁用 MySQL 专有语法。
- `schema.sql`/`data.sql` 每次启动执行（`spring.sql.init.mode: always`），必须幂等（`NOT EXISTS` 守卫）。
- 密钥（私钥路径、kid、iss、aid）经环境变量注入，不入库；`config/iap/` 加入 `.gitignore`。
- 强类型 DTO/payload，禁用 `Map`/`JsonNode` 作对外 DTO（`JsonNode` 仅在解析华为返回的 JWS payload 内部使用）。
- 测试：`@SpringBootTest` + `@MockitoBean StringRedisTemplate` + `@Transactional`；纯逻辑类用普通 JUnit。运行：`./mvnw test`。

参考来源（技能 references，已读取）：`references/client-arkts.md`（数据结构）、`references/server-java.md`（Java demo：IAPServer/JWTGenerator/JWSChecker/OrderService/SubscriptionService/notification）。

## File Structure

新建/修改文件清单：

- 修改：`pom.xml`（加 java-jwt、commons-codec、bouncycastle-test）
- 修改：`src/main/resources/application.yaml`、`src/test/resources/application.yaml`（加 `app.iap`）
- 修改：`src/main/resources/schema.sql`（加 4 张表，分散在各任务）
- 修改：`src/main/resources/data.sql`（IAP_PURCHASE 权限、iap_product 种子、移除旧权限种子）
- 修改：`src/main/java/.../MusicBackServiceApplication.java`（@MapperScan 加 iap.mapper；@EnableConfigurationProperties 加 IapProperties）
- 修改：`src/main/java/.../config/MyBatisConfig.java`（@MapperScan 加 iap.mapper）
- 修改：`src/main/java/.../auth/AuthInterceptor.java`（公开 `/api/iap/notifications`）
- 修改：`src/main/java/.../membership/domain/PointsChangeReason.java`（加 `IAP_REVOKE`）
- 修改：`src/main/java/.../membership/service/MembershipService.java`（加回收方法）
- 修改：`src/main/java/.../membership/controller/MembershipController.java`（删除旧购买接口）
- 修改：`.gitignore`（加 `config/iap/`）
- 新建 `iap/domain/`：`IapProductType`、`InternalProductType`、`OrderStatus`、`FulfillmentAction`、`IapNotificationType`、`IapProductEntity`、`IapOrderEntity`、`IapFulfillmentEntity`、`IapNotificationLogEntity`
- 新建 `iap/dto/payload/`：`PurchaseData`、`PurchaseOrderPayload`、`SubGroupStatusPayload`、`SubscriptionStatus`、`SubRenewalInfo`、`NotificationPayload`、`NotificationMetaData`
- 新建 `iap/dto/request/`：`CreateOrderRequest`、`ReportPurchaseRequest`、`IapNotificationRequest`
- 新建 `iap/dto/response/`：`IapProductResponse`、`PreOrderResponse`、`ReportPurchaseResponse`
- 新建 `iap/mapper/`：`IapProductMapper`、`IapOrderMapper`、`IapFulfillmentMapper`、`IapNotificationLogMapper`
- 新建 `iap/support/`：`IapProperties`、`JwsVerifier`、`IapJwtGenerator`
- 新建 `iap/service/`：`IapProductService`、`IapOrderService`、`IapFulfillmentService`、`IapNotificationService`、`IapHuaweiClient`
- 新建 `iap/controller/`：`IapController`、`IapNotificationController`
- 新建测试：`iap/support/JwsVerifierTest`、`iap/support/IapJwtGeneratorTest`、`iap/support/TestCertFactory`、`iap/domain/IapPayloadMappingTest`、`iap/service/IapFulfillmentServiceIntegrationTest`、`iap/service/IapNotificationServiceIntegrationTest`、`iap/IapEndToEndIntegrationTest`

---

### Task 1: 基础设施（依赖、配置、MapperScan、权限、公开端点、gitignore）

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/lemon/music/musicbackservice/iap/support/IapProperties.java`
- Modify: `src/main/resources/application.yaml`、`src/test/resources/application.yaml`
- Modify: `MusicBackServiceApplication.java`、`config/MyBatisConfig.java`、`auth/AuthInterceptor.java`
- Modify: `src/main/resources/data.sql`、`.gitignore`
- Test: `src/test/java/com/lemon/music/musicbackservice/iap/IapInfrastructureTest.java`

**Interfaces:**
- Produces: `IapProperties`（`baseUrl()`/`rootCaCertPath()`/`jwt()`，`jwt()` 含 `privateKeyPath/keyId/issuerId/appId`）；`@MapperScan` 已含 `iap.mapper`；`/api/iap/notifications` 公开；权限 `IAP_PURCHASE` 已种子并授予 USER/ADMIN。

- [ ] **Step 1: 加依赖到 `pom.xml`**

在 `<dependencies>` 内、`spring-security-crypto` 依赖之后加入：

```xml
<dependency>
    <groupId>com.auth0</groupId>
    <artifactId>java-jwt</artifactId>
    <version>4.4.0</version>
</dependency>
<dependency>
    <groupId>commons-codec</groupId>
    <artifactId>commons-codec</artifactId>
    <version>1.18.0</version>
</dependency>
```

在 `h2` 测试依赖之后加入（仅测试用，生成验签所需的测试证书链）：

```xml
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcpkix-jdk18on</artifactId>
    <version>1.78.1</version>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: 创建 `IapProperties.java`**

```java
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
```

- [ ] **Step 3: `MusicBackServiceApplication.java` 注册 IapProperties 并扩 MapperScan**

把 `@EnableConfigurationProperties(AuthProperties.class)` 改为 `@EnableConfigurationProperties({AuthProperties.class, IapProperties.class})`，并加 import `com.lemon.music.musicbackservice.iap.support.IapProperties`。

把 `@MapperScan({...})` 改为：
```java
@MapperScan({"com.lemon.music.musicbackservice.user.mapper",
             "com.lemon.music.musicbackservice.membership.mapper",
             "com.lemon.music.musicbackservice.iap.mapper"})
```

- [ ] **Step 4: `config/MyBatisConfig.java` 同步扩 MapperScan**

把该类的 `@MapperScan({...})` 改为与上一步相同的三个包。

- [ ] **Step 5: `application.yaml` 加 `app.iap`**

在文件末尾的 `app:` 节点下追加：
```yaml
  iap:
    base-url: https://iap.cloud.huawei.com
    root-ca-cert-path: ${user.dir}/config/iap/RootCaG2Ecdsa.cer
    jwt:
      private-key-path: ${user.dir}/config/iap/priKey.p8
      key-id: ${IAP_KEY_ID:}
      issuer-id: ${IAP_ISSUER_ID:}
      app-id: ${IAP_APP_ID:}
```

- [ ] **Step 6: `src/test/resources/application.yaml` 加 `app.iap`**

在文件末尾的 `app:` 节点下追加（测试用占位值；证书/私钥文件可缺失，加载逻辑会降级）：
```yaml
  iap:
    base-url: https://iap.test
    root-ca-cert-path: ${user.dir}/config/iap/RootCaG2Ecdsa.cer
    jwt:
      private-key-path: ${user.dir}/config/iap/priKey.p8
      key-id: test-kid
      issuer-id: test-iss
      app-id: test-aid
```

- [ ] **Step 7: `AuthInterceptor.java` 公开通知端点**

把 `preHandle` 中白名单判断：
```java
if (path.startsWith("/api/auth/login") || path.startsWith("/api/users/register")) {
    return true;
}
```
改为：
```java
if (path.startsWith("/api/auth/login") || path.startsWith("/api/users/register")
        || path.startsWith("/api/iap/notifications")) {
    return true;
}
```

- [ ] **Step 8: `data.sql` 加 IAP_PURCHASE 权限并授予 USER/ADMIN；移除旧权限种子**

删除文件中 `MEMBERSHIP_PURCHASE`、`PRODUCT_PURCHASE`、`PRODUCT_USE` 三段权限 `INSERT` 以及所有引用这三个权限码的 `role_permission` `INSERT`（即"会员系统权限"整段与下面两段 role_permission）。替换为：

```sql
-- ==================== IAP 购买权限 ====================

INSERT INTO app_permission(permission_code, description)
SELECT 'IAP_PURCHASE', 'IAP 商品购买（预下单/上报）'
WHERE NOT EXISTS (SELECT 1 FROM app_permission WHERE permission_code = 'IAP_PURCHASE');

-- 普通用户与管理员均可发起 IAP 购买
INSERT INTO role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM app_role r
JOIN app_permission p ON p.permission_code = 'IAP_PURCHASE'
WHERE r.role_code IN ('USER', 'ADMIN')
  AND NOT EXISTS (
      SELECT 1 FROM role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
```

- [ ] **Step 9: `.gitignore` 加 `config/iap/`**

在 `.gitignore` 末尾追加一行：
```
config/iap/
```

- [ ] **Step 10: 写失败测试 `IapInfrastructureTest.java`**

```java
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
```

- [ ] **Step 11: 运行测试，确认通过**

Run: `./mvnw test -Dtest=IapInfrastructureTest`
Expected: PASS（上下文启动成功，IapProperties 绑定成功）。

- [ ] **Step 12: 提交**

```bash
git add pom.xml src/main/java/com/lemon/music/musicbackservice/iap/support/IapProperties.java \
  src/main/java/com/lemon/music/musicbackservice/MusicBackServiceApplication.java \
  src/main/java/com/lemon/music/musicbackservice/config/MyBatisConfig.java \
  src/main/java/com/lemon/music/musicbackservice/auth/AuthInterceptor.java \
  src/main/resources/application.yaml src/test/resources/application.yaml \
  src/main/resources/data.sql .gitignore \
  src/test/java/com/lemon/music/musicbackservice/iap/IapInfrastructureTest.java
git commit -m "feat(iap): 基础设施-依赖/配置/MapperScan/权限/公开端点"
```

---

### Task 2: IAP 枚举与 payload 强类型 records

**Files:**
- Create: `iap/domain/IapProductType.java`、`InternalProductType.java`、`OrderStatus.java`、`FulfillmentAction.java`、`IapNotificationType.java`
- Create: `iap/dto/payload/PurchaseData.java`、`PurchaseOrderPayload.java`、`SubGroupStatusPayload.java`、`SubscriptionStatus.java`、`SubRenewalInfo.java`、`NotificationPayload.java`、`NotificationMetaData.java`
- Test: `src/test/java/com/lemon/music/musicbackservice/iap/domain/IapPayloadMappingTest.java`

**Interfaces:**
- Produces: `IapProductType`（`getCode()`、`fromCode(int)`）、4 个简单枚举、7 个 payload record（字段对齐 `references/client-arkts.md`）。

- [ ] **Step 1: 创建枚举**

`iap/domain/IapProductType.java`：
```java
package com.lemon.music.musicbackservice.iap.domain;

/** IAP 商品类型，code 对应华为 PurchaseData.type 数字。 */
public enum IapProductType {
    CONSUMABLE(0), NON_CONSUMABLE(1), AUTORENEWABLE(2), NONRENEWABLE(3);

    private final int code;

    IapProductType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static IapProductType fromCode(int code) {
        for (IapProductType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown IapProductType code: " + code);
    }
}
```

`iap/domain/InternalProductType.java`：
```java
package com.lemon.music.musicbackservice.iap.domain;

/** 内部商品来源：product 表商品 / 订阅类型商品。 */
public enum InternalProductType {
    PRODUCT, SUBSCRIPTION
}
```

`iap/domain/OrderStatus.java`：
```java
package com.lemon.music.musicbackservice.iap.domain;

/** 业务订单状态。 */
public enum OrderStatus {
    PENDING, PAID, FULFILLED, REFUNDED, CLOSED
}
```

`iap/domain/FulfillmentAction.java`：
```java
package com.lemon.music.musicbackservice.iap.domain;

/** 权益动作：发放 / 回收。 */
public enum FulfillmentAction {
    GRANT, REVOKE
}
```

`iap/domain/IapNotificationType.java`：
```java
package com.lemon.music.musicbackservice.iap.domain;

/** 华为关键事件通知类型。 */
public enum IapNotificationType {
    DID_NEW_TRANSACTION, DID_CHANGE_RENEWAL_STATUS, REVOKE, RENEWAL_TIME_MODIFIED, EXPIRE
}
```

- [ ] **Step 2: 创建 payload records（字段严格对齐 client-arkts.md）**

`iap/dto/payload/PurchaseData.java`：
```java
package com.lemon.music.musicbackservice.iap.dto.payload;

/** createPurchase / queryPurchases 返回的 purchaseData 解析结构。 */
public record PurchaseData(
        Integer type,
        String jwsPurchaseOrder,
        String jwsSubscriptionStatus
) {}
```

`iap/dto/payload/PurchaseOrderPayload.java`：
```java
package com.lemon.music.musicbackservice.iap.dto.payload;

/** jwsPurchaseOrder 验签解码后的订单载荷。 */
public record PurchaseOrderPayload(
        String applicationId,
        String productId,
        Integer productType,
        String purchaseOrderId,
        String purchaseToken,
        Long purchaseTime,
        Long signedTime,
        String countryCode,
        Double price,
        String currency,
        String environment,
        String finishStatus,
        Boolean needFinish,
        String developerPayload,
        String purchaseOrderRevocationReasonCode,
        String offerId,
        String subscriptionId,
        String subGroupGenerationId
) {}
```

`iap/dto/payload/SubRenewalInfo.java`：
```java
package com.lemon.music.musicbackservice.iap.dto.payload;

public record SubRenewalInfo(String productId) {}
```

`iap/dto/payload/SubscriptionStatus.java`：
```java
package com.lemon.music.musicbackservice.iap.dto.payload;

public record SubscriptionStatus(
        String subGroupGenerationId,
        String subscriptionId,
        String purchaseToken,
        String status,
        Long expiresTime,
        PurchaseOrderPayload lastPurchaseOrder,
        SubRenewalInfo renewalInfo
) {}
```

`iap/dto/payload/SubGroupStatusPayload.java`：
```java
package com.lemon.music.musicbackservice.iap.dto.payload;

import java.util.List;

/** jwsSubscriptionStatus 验签解码后的订阅组状态载荷。 */
public record SubGroupStatusPayload(
        String environment,
        String applicationId,
        String packageName,
        String subGroupId,
        SubscriptionStatus lastSubscriptionStatus,
        List<SubscriptionStatus> historySubscriptionStatusList
) {}
```

`iap/dto/payload/NotificationMetaData.java`：
```java
package com.lemon.music.musicbackservice.iap.dto.payload;

public record NotificationMetaData(
        String environment,
        String applicationId,
        String packageName,
        Integer type,
        String currentProductId,
        String subGroupId,
        String subGroupGenerationId,
        String subscriptionId,
        String purchaseToken,
        String purchaseOrderId
) {}
```

`iap/dto/payload/NotificationPayload.java`：
```java
package com.lemon.music.musicbackservice.iap.dto.payload;

/** 通知 JWS 验签解码后的载荷。 */
public record NotificationPayload(
        String notificationType,
        String notificationSubtype,
        String notificationRequestId,
        NotificationMetaData notificationMetaData,
        String notificationVersion,
        Long signedTime
) {}
```

- [ ] **Step 3: 写失败测试 `IapPayloadMappingTest.java`**

```java
package com.lemon.music.musicbackservice.iap.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemon.music.musicbackservice.iap.dto.payload.NotificationPayload;
import com.lemon.music.musicbackservice.iap.dto.payload.PurchaseData;
import com.lemon.music.musicbackservice.iap.dto.payload.PurchaseOrderPayload;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IapPayloadMappingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void iapProductTypeFromCode() {
        assertThat(IapProductType.fromCode(0)).isEqualTo(IapProductType.CONSUMABLE);
        assertThat(IapProductType.fromCode(2)).isEqualTo(IapProductType.AUTORENEWABLE);
        assertThat(IapProductType.AUTORENEWABLE.getCode()).isEqualTo(2);
    }

    @Test
    void purchaseDataParses() throws Exception {
        String json = "{\"type\":0,\"jwsPurchaseOrder\":\"abc.jwt.def\"}";
        PurchaseData data = mapper.readValue(json, PurchaseData.class);
        assertThat(data.type()).isEqualTo(0);
        assertThat(data.jwsPurchaseOrder()).isEqualTo("abc.jwt.def");
    }

    @Test
    void purchaseOrderPayloadParses() throws Exception {
        String json = "{\"productId\":\"P1\",\"purchaseOrderId\":\"PO1\",\"purchaseToken\":\"T1\","
                + "\"developerPayload\":\"ORDER_NO_123\",\"finishStatus\":\"2\","
                + "\"purchaseOrderRevocationReasonCode\":null,\"productType\":0}";
        PurchaseOrderPayload p = mapper.readValue(json, PurchaseOrderPayload.class);
        assertThat(p.productId()).isEqualTo("P1");
        assertThat(p.developerPayload()).isEqualTo("ORDER_NO_123");
        assertThat(p.finishStatus()).isEqualTo("2");
    }

    @Test
    void notificationPayloadParses() throws Exception {
        String json = "{\"notificationType\":\"REVOKE\",\"notificationSubtype\":\"REFUND_TRANSACTION\","
                + "\"notificationRequestId\":\"REQ1\",\"notificationVersion\":\"v1\",\"signedTime\":1,"
                + "\"notificationMetaData\":{\"type\":0,\"currentProductId\":\"P1\","
                + "\"purchaseOrderId\":\"PO1\",\"purchaseToken\":\"T1\"}}";
        NotificationPayload n = mapper.readValue(json, NotificationPayload.class);
        assertThat(n.notificationType()).isEqualTo("REVOKE");
        assertThat(n.notificationMetaData().purchaseOrderId()).isEqualTo("PO1");
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

Run: `./mvnw test -Dtest=IapPayloadMappingTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/lemon/music/musicbackservice/iap/domain \
  src/main/java/com/lemon/music/musicbackservice/iap/dto/payload \
  src/test/java/com/lemon/music/musicbackservice/iap/domain/IapPayloadMappingTest.java
git commit -m "feat(iap): IAP 枚举与 payload 强类型 records"
```

---

### Task 3: JwsVerifier（JWS 验签解码）+ 测试证书工厂

**Files:**
- Create: `src/test/java/com/lemon/music/musicbackservice/iap/support/TestCertFactory.java`
- Create: `src/main/java/com/lemon/music/musicbackservice/iap/support/JwsVerifier.java`
- Test: `src/test/java/com/lemon/music/musicbackservice/iap/support/JwsVerifierTest.java`

**Interfaces:**
- Consumes: `IapProperties.rootCaCertPath()`（构造时加载根证书；加载失败降级为空 anchor + 警告，保证上下文启动）
- Produces: `JwsVerifier`（`@Component`，`String checkAndDecode(String jws)`）；静态 `verify(String jws, Set<TrustAnchor> anchors)` 供测试直接使用。

- [ ] **Step 1: 创建测试证书工厂 `TestCertFactory.java`**

生成 ES256 三层证书链（root→intermediate→leaf，leaf 含华为叶子 OID），并能用 leaf 私钥签发 JWS。

```java
package com.lemon.music.musicbackservice.iap.support;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/** 测试专用：生成 IAP 风格的 ES256 证书链（leaf 含华为叶子 OID）并签发 JWS。 */
public final class TestCertFactory {

    /** 华为 IAP 叶子证书标识 OID。 */
    public static final String LEAF_OID = "1.3.6.1.4.1.2011.2.415.1.1";
    private static final String SIGN_ALG = "SHA256withECDSA";

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public final X509Certificate root;
    public final X509Certificate intermediate;
    public final X509Certificate leaf;
    public final KeyPair leafKeyPair;

    public TestCertFactory() throws Exception {
        KeyPair rootKp = ecKeyPair();
        KeyPair intKp = ecKeyPair();
        leafKeyPair = ecKeyPair();
        root = issue("CN=IapTestRoot", rootKp, "CN=IapTestRoot", rootKp, true, null);
        intermediate = issue("CN=IapTestIntermediate", intKp, "CN=IapTestRoot", rootKp, true, null);
        leaf = issue("CN=IapTestLeaf", leafKeyPair, "CN=IapTestIntermediate", intKp, false, LEAF_OID);
    }

    private KeyPair ecKeyPair() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(256);
        return g.generateKeyPair();
    }

    private X509Certificate issue(String subject, KeyPair subjectKp, String issuerName,
                                  KeyPair issuerKp, boolean ca, String oid) throws Exception {
        Instant now = Instant.now();
        ContentSigner signer = new JcaContentSignerBuilder(SIGN_ALG).build(issuerKp.getPrivate());
        JcaX509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(
                new X500Name(issuerName),
                BigInteger.valueOf(System.nanoTime()),
                Date.from(now.minus(1, ChronoUnit.DAYS)),
                Date.from(now.plus(365, ChronoUnit.DAYS)),
                new X500Name(subject),
                subjectKp.getPublic());
        b.addExtension(Extension.basicConstraints, true, new BasicConstraints(ca));
        if (oid != null) {
            b.addExtension(new ASN1ObjectIdentifier(oid), false, DERNull.INSTANCE);
        }
        return new JcaX509CertificateConverter().getCertificate(b.build(signer));
    }

    /** x5c 数组顺序：[leaf, intermediate, root]，用于构造 JWS header。 */
    public String[] x5cChain() {
        return new String[]{
                b64(leaf.getEncoded()),
                b64(intermediate.getEncoded()),
                b64(root.getEncoded())
        };
    }

    /** 用 leaf 私钥签发 JWS（header 带 x5c，payload 任意 JSON）。 */
    public String signJws(String payloadJson) throws Exception {
        Map<String, Object> payload = new ObjectMapper().readValue(payloadJson, new TypeReference<>() {});
        return JWT.create()
                .withHeader(Map.of("alg", "ES256", "typ", "JWT", "x5c", x5cChain()))
                .withPayload(payload)
                .sign(Algorithm.ECDSA256((ECPrivateKey) leafKeyPair.getPrivate()));
    }

    private static String b64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }
}
```

- [ ] **Step 2: 创建 `JwsVerifier.java`**

移植 `references/server-java.md` 的 `JWSChecker`。根证书从 `IapProperties` 加载；加载失败降级为空 anchor（生产部署前必须配置真实证书）。

```java
package com.lemon.music.musicbackservice.iap.support;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/** JWS 验签：校验 ES256 + x5c 证书链（PKIX）+ 叶子证书 OID，返回 payload JSON。 */
@Slf4j
@Component
public class JwsVerifier {

    private static final String HEADER_X5C = "x5c";
    private static final String ALG_ES256 = "ES256";
    private static final int X5C_CHAIN_LENGTH = 3;
    private static final String LEAF_CERT_OID = "1.3.6.1.4.1.2011.2.415.1.1";

    private final Set<TrustAnchor> trustAnchors;

    public JwsVerifier(IapProperties properties) {
        this.trustAnchors = loadRootCa(properties.rootCaCertPath());
    }

    /** 生产入口：用配置的根证书验签。 */
    public String checkAndDecode(String jws) throws Exception {
        return verify(jws, trustAnchors);
    }

    /** 纯逻辑验签（供测试直接传入 anchor）。 */
    public static String verify(String jws, Set<TrustAnchor> anchors) throws Exception {
        if (jws == null || jws.isEmpty()) {
            throw new IllegalArgumentException("jws 为空");
        }
        DecodedJWT decoded = JWT.decode(jws);
        if (!ALG_ES256.equals(decoded.getAlgorithm())) {
            throw new IllegalArgumentException("alg 必须为 ES256");
        }
        String[] x5c = decoded.getHeaderClaim(HEADER_X5C).asArray(String.class);
        if (x5c == null) {
            throw new IllegalArgumentException("x5c 证书链为空");
        }
        PublicKey publicKey = verifyChainAndGetPubKey(x5c, anchors);
        JWTVerifier verifier = JWT.require(Algorithm.ECDSA256((ECPublicKey) publicKey)).build();
        verifier.verify(decoded);
        return new String(Base64.getUrlDecoder().decode(decoded.getPayload()), StandardCharsets.UTF_8);
    }

    private static PublicKey verifyChainAndGetPubKey(String[] certs, Set<TrustAnchor> anchors) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        List<Certificate> list = new LinkedList<>();
        for (String c : certs) {
            list.add(cf.generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(c))));
        }
        if (list.size() != X5C_CHAIN_LENGTH) {
            throw new IllegalArgumentException("证书链长度必须为 3");
        }
        PKIXParameters params = new PKIXParameters(anchors);
        params.setRevocationEnabled(false);
        java.security.cert.CertPath path = cf.generateCertPath(list.subList(0, X5C_CHAIN_LENGTH - 1));
        PKIXCertPathValidatorResult result = (PKIXCertPathValidatorResult)
                java.security.cert.CertPathValidator.getInstance("PKIX").validate(path, params);

        X509Certificate leaf = (X509Certificate) list.get(0);
        if (leaf.getNonCriticalExtensionOIDs() == null
                || !leaf.getNonCriticalExtensionOIDs().contains(LEAF_CERT_OID)) {
            throw new IllegalArgumentException("叶子证书缺少 IAP OID");
        }
        return result.getPublicKey();
    }

    private static Set<TrustAnchor> loadRootCa(String path) {
        Set<TrustAnchor> anchors = new HashSet<>();
        if (path == null || path.isBlank()) {
            return anchors;
        }
        try {
            byte[] der;
            if (path.startsWith("classpath:")) {
                String res = path.substring("classpath:".length());
                der = JwsVerifier.class.getClassLoader().getResourceAsStream(res).readAllBytes();
            } else {
                der = Files.readAllBytes(Paths.get(path));
            }
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate root = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(der));
            anchors.add(new TrustAnchor(root, null));
        } catch (Exception e) {
            log.warn("加载 IAP 根证书失败（部署前须配置 app.iap.root-ca-cert-path）: {}", path);
        }
        return anchors;
    }
}
```

- [ ] **Step 3: 写测试 `JwsVerifierTest.java`**

```java
package com.lemon.music.musicbackservice.iap.support;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.cert.TrustAnchor;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwsVerifierTest {

    private static TestCertFactory factory;
    private static Set<TrustAnchor> anchors;

    @BeforeAll
    static void setup() throws Exception {
        factory = new TestCertFactory();
        anchors = Set.of(new TrustAnchor(factory.root, null));
    }

    @Test
    void verifiesValidJwsAndDecodesPayload() throws Exception {
        String payload = "{\"notificationType\":\"REVOKE\"}";
        String jws = factory.signJws(payload);

        String decoded = JwsVerifier.verify(jws, anchors);

        assertThat(decoded).contains("\"notificationType\":\"REVOKE\"");
    }

    @Test
    void rejectsTamperedJws() throws Exception {
        String jws = factory.signJws("{\"a\":1}");
        String tampered = jws.substring(0, jws.length() - 5) + "AAAAA";

        assertThatThrownBy(() -> JwsVerifier.verify(tampered, anchors))
                .isInstanceOf(Exception.class);
    }

    @Test
    void rejectsEmptyJws() {
        assertThatThrownBy(() -> JwsVerifier.verify("", anchors))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

Run: `./mvnw test -Dtest=JwsVerifierTest`
Expected: PASS（三个用例：合法解码、篡改拒绝、空串拒绝）。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/lemon/music/musicbackservice/iap/support/JwsVerifier.java \
  src/test/java/com/lemon/music/musicbackservice/iap/support/TestCertFactory.java \
  src/test/java/com/lemon/music/musicbackservice/iap/support/JwsVerifierTest.java
git commit -m "feat(iap): JWS 验签解码 + 测试证书工厂"
```


### Task 4: IapJwtGenerator（调用华为 REST 的 ES256 JWT）

**Files:**
- Create: `src/main/java/com/lemon/music/musicbackservice/iap/support/IapJwtGenerator.java`
- Test: `src/test/java/com/lemon/music/musicbackservice/iap/support/IapJwtGeneratorTest.java`

**Interfaces:**
- Consumes: `IapProperties.jwt()`（私钥路径、kid、iss、aid）
- Produces: `IapJwtGenerator`（`@Component`，`String generate(String bodyJson)`）；静态 `sign(JwtClaims claims, ECPrivateKey key)` 供测试。

- [ ] **Step 1: 创建 `IapJwtGenerator.java`**

移植 `references/server-java.md` 的 `JWTGenerator`。digest = SHA256(body)。

```java
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
```

- [ ] **Step 2: 写测试 `IapJwtGeneratorTest.java`**

```java
package com.lemon.music.musicbackservice.iap.support;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IapJwtGeneratorTest {

    @Test
    void jwtContainsDigestAndAudience() throws Exception {
        TestCertFactory factory = new TestCertFactory();
        String body = "{\"purchaseOrderId\":\"PO1\"}";

        // 直接用 TestCertFactory 的私钥构造一个 JWT 验证 digest 计算逻辑
        var header = new java.util.HashMap<String, Object>();
        header.put("alg", "ES256");
        var payload = new java.util.HashMap<String, Object>();
        payload.put("aud", "iap-v1");
        payload.put("iss", "test-iss");
        payload.put("aid", "test-aid");
        payload.put("digest", DigestUtils.sha256Hex(body));
        String jwt = JWT.create().withHeader(header).withPayload(payload)
                .sign(Algorithm.ECDSA256(factory.leafKeyPair.getPrivate()));

        var decoded = JWT.decode(jwt);
        assertThat(decoded.getClaim("aud").asString()).isEqualTo("iap-v1");
        assertThat(decoded.getClaim("digest").asString()).isEqualTo(DigestUtils.sha256Hex(body));
    }
}
```

- [ ] **Step 3: 运行测试，确认通过**

Run: `./mvnw test -Dtest=IapJwtGeneratorTest`
Expected: PASS。

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/lemon/music/musicbackservice/iap/support/IapJwtGenerator.java \
  src/test/java/com/lemon/music/musicbackservice/iap/support/IapJwtGeneratorTest.java
git commit -m "feat(iap): 调用华为 REST 的 ES256 JWT 生成"
```

---

### Task 5: iap_product 数据层 + IapProductService + 商品查询接口

**Files:**
- Modify: `src/main/resources/schema.sql`（加 iap_product 表）
- Modify: `src/main/resources/data.sql`（加 iap_product 种子）
- Create: `iap/domain/IapProductEntity.java`、`iap/mapper/IapProductMapper.java`、`iap/service/IapProductService.java`、`iap/dto/response/IapProductResponse.java`、`iap/controller/IapController.java`
- Test: `src/test/java/com/lemon/music/musicbackservice/iap/service/IapProductQueryIntegrationTest.java`

**Interfaces:**
- Consumes: 无（首个数据层任务）
- Produces: `IapProductEntity`、`IapProductMapper`（`findById`/`findActive`/`findByHuaweiProductId`）、`IapProductService.getAvailableProducts()`、`IapProductService.getById(Long)`、`IapController`（挂 `/api/iap`，`GET /products`）。

- [ ] **Step 1: `schema.sql` 末尾加 `iap_product` 表**

```sql

-- ==================== IAP 对接 ====================

-- IAP 商品映射表（统一管理消耗/非消耗/订阅）
CREATE TABLE IF NOT EXISTS iap_product (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    internal_type VARCHAR(16) NOT NULL,
    internal_ref_id BIGINT NULL,
    subscription_type VARCHAR(16) NULL,
    huawei_product_id VARCHAR(128) NOT NULL,
    iap_product_type VARCHAR(24) NOT NULL,
    name VARCHAR(64) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_iap_product_huawei_id UNIQUE (huawei_product_id)
);
```

- [ ] **Step 2: `data.sql` 末尾加 iap_product 种子（8 行）**

```sql

-- ==================== IAP 商品映射种子 ====================

INSERT INTO iap_product(internal_type, internal_ref_id, huawei_product_id, iap_product_type, name, price, currency, status, created_at, updated_at)
SELECT 'PRODUCT', p.id, 'iap_gem_card_001', 'CONSUMABLE', '宝石等级加速卡', 9.90, 'CNY', 'ACTIVE', NOW(), NOW()
FROM product p
WHERE p.product_name = '宝石等级加速卡'
  AND NOT EXISTS (SELECT 1 FROM iap_product ip WHERE ip.huawei_product_id = 'iap_gem_card_001');

INSERT INTO iap_product(internal_type, internal_ref_id, huawei_product_id, iap_product_type, name, price, currency, status, created_at, updated_at)
SELECT 'PRODUCT', p.id, 'iap_lifetime_svip_001', 'NON_CONSUMABLE', '终身顶级卡', 1999.00, 'CNY', 'ACTIVE', NOW(), NOW()
FROM product p
WHERE p.product_name = '终身顶级卡'
  AND NOT EXISTS (SELECT 1 FROM iap_product ip WHERE ip.huawei_product_id = 'iap_lifetime_svip_001');

INSERT INTO iap_product(internal_type, subscription_type, huawei_product_id, iap_product_type, name, price, currency, status, created_at, updated_at)
SELECT 'SUBSCRIPTION', 'MONTHLY', 'iap_sub_monthly_auto', 'AUTORENEWABLE', '月卡(连续包月)', 18.00, 'CNY', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM iap_product ip WHERE ip.huawei_product_id = 'iap_sub_monthly_auto');

INSERT INTO iap_product(internal_type, subscription_type, huawei_product_id, iap_product_type, name, price, currency, status, created_at, updated_at)
SELECT 'SUBSCRIPTION', 'QUARTERLY', 'iap_sub_quarterly_auto', 'AUTORENEWABLE', '季卡(连续包季)', 48.00, 'CNY', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM iap_product ip WHERE ip.huawei_product_id = 'iap_sub_quarterly_auto');

INSERT INTO iap_product(internal_type, subscription_type, huawei_product_id, iap_product_type, name, price, currency, status, created_at, updated_at)
SELECT 'SUBSCRIPTION', 'YEARLY', 'iap_sub_yearly_auto', 'AUTORENEWABLE', '年卡(连续包年)', 168.00, 'CNY', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM iap_product ip WHERE ip.huawei_product_id = 'iap_sub_yearly_auto');

INSERT INTO iap_product(internal_type, subscription_type, huawei_product_id, iap_product_type, name, price, currency, status, created_at, updated_at)
SELECT 'SUBSCRIPTION', 'MONTHLY', 'iap_sub_monthly_once', 'NONRENEWABLE', '月卡(一次性)', 18.00, 'CNY', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM iap_product ip WHERE ip.huawei_product_id = 'iap_sub_monthly_once');

INSERT INTO iap_product(internal_type, subscription_type, huawei_product_id, iap_product_type, name, price, currency, status, created_at, updated_at)
SELECT 'SUBSCRIPTION', 'QUARTERLY', 'iap_sub_quarterly_once', 'NONRENEWABLE', '季卡(一次性)', 48.00, 'CNY', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM iap_product ip WHERE ip.huawei_product_id = 'iap_sub_quarterly_once');

INSERT INTO iap_product(internal_type, subscription_type, huawei_product_id, iap_product_type, name, price, currency, status, created_at, updated_at)
SELECT 'SUBSCRIPTION', 'YEARLY', 'iap_sub_yearly_once', 'NONRENEWABLE', '年卡(一次性)', 168.00, 'CNY', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM iap_product ip WHERE ip.huawei_product_id = 'iap_sub_yearly_once');
```

- [ ] **Step 3: 创建 `IapProductEntity.java`**

```java
package com.lemon.music.musicbackservice.iap.domain;

import com.lemon.music.musicbackservice.membership.domain.SubscriptionType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class IapProductEntity {
    private Long id;
    private InternalProductType internalType;
    private Long internalRefId;
    private SubscriptionType subscriptionType;
    private String huaweiProductId;
    private IapProductType iapProductType;
    private String name;
    private BigDecimal price;
    private String currency;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 4: 创建 `IapProductMapper.java`**

```java
package com.lemon.music.musicbackservice.iap.mapper;

import com.lemon.music.musicbackservice.iap.domain.IapProductEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface IapProductMapper {

    @Select("SELECT id, internal_type, internal_ref_id, subscription_type, huawei_product_id, "
            + "iap_product_type, name, price, currency, status, created_at, updated_at "
            + "FROM iap_product WHERE id = #{id}")
    IapProductEntity findById(@Param("id") Long id);

    @Select("SELECT id, internal_type, internal_ref_id, subscription_type, huawei_product_id, "
            + "iap_product_type, name, price, currency, status, created_at, updated_at "
            + "FROM iap_product WHERE status = 'ACTIVE' ORDER BY id")
    List<IapProductEntity> findActive();

    @Select("SELECT id, internal_type, internal_ref_id, subscription_type, huawei_product_id, "
            + "iap_product_type, name, price, currency, status, created_at, updated_at "
            + "FROM iap_product WHERE huawei_product_id = #{huaweiProductId}")
    IapProductEntity findByHuaweiProductId(@Param("huaweiProductId") String huaweiProductId);
}
```

- [ ] **Step 5: 创建 `IapProductResponse.java`**

```java
package com.lemon.music.musicbackservice.iap.dto.response;

import com.lemon.music.musicbackservice.iap.domain.IapProductType;
import com.lemon.music.musicbackservice.membership.domain.SubscriptionType;

import java.math.BigDecimal;

public record IapProductResponse(
        Long id,
        String name,
        String huaweiProductId,
        IapProductType iapProductType,
        BigDecimal price,
        String currency,
        SubscriptionType subscriptionType,
        String description
) {}
```

- [ ] **Step 6: 创建 `IapProductService.java`**

```java
package com.lemon.music.musicbackservice.iap.service;

import com.lemon.music.musicbackservice.common.BusinessException;
import com.lemon.music.musicbackservice.iap.domain.IapProductEntity;
import com.lemon.music.musicbackservice.iap.dto.response.IapProductResponse;
import com.lemon.music.musicbackservice.iap.mapper.IapProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class IapProductService {

    private final IapProductMapper iapProductMapper;

    public List<IapProductResponse> getAvailableProducts() {
        return iapProductMapper.findActive().stream()
                .map(p -> new IapProductResponse(p.getId(), p.getName(), p.getHuaweiProductId(),
                        p.getIapProductType(), p.getPrice(), p.getCurrency(), p.getSubscriptionType(),
                        p.getName()))
                .toList();
    }

    public IapProductEntity getById(Long id) {
        IapProductEntity product = iapProductMapper.findById(id);
        if (product == null) {
            throw new BusinessException("IAP 商品不存在");
        }
        return product;
    }
}
```

- [ ] **Step 7: 创建 `IapController.java`（先只放商品查询；后续任务追加预下单/上报）**

```java
package com.lemon.music.musicbackservice.iap.controller;

import com.lemon.music.musicbackservice.common.ApiResponse;
import com.lemon.music.musicbackservice.iap.dto.response.IapProductResponse;
import com.lemon.music.musicbackservice.iap.service.IapProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/iap")
@RequiredArgsConstructor
public class IapController {

    private final IapProductService iapProductService;

    /** 查询上架的 IAP 商品列表（登录即可）。 */
    @GetMapping("/products")
    public ApiResponse<List<IapProductResponse>> getProducts() {
        return ApiResponse.ok(iapProductService.getAvailableProducts());
    }
}
```

- [ ] **Step 8: 写测试 `IapProductQueryIntegrationTest.java`**

```java
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
```

- [ ] **Step 9: 运行测试，确认通过**

Run: `./mvnw test -Dtest=IapProductQueryIntegrationTest`
Expected: PASS（8 行种子，含 CONSUMABLE/AUTORENEWABLE/NONRENEWABLE）。

- [ ] **Step 10: 提交**

```bash
git add src/main/resources/schema.sql src/main/resources/data.sql \
  src/main/java/com/lemon/music/musicbackservice/iap/domain/IapProductEntity.java \
  src/main/java/com/lemon/music/musicbackservice/iap/mapper/IapProductMapper.java \
  src/main/java/com/lemon/music/musicbackservice/iap/service/IapProductService.java \
  src/main/java/com/lemon/music/musicbackservice/iap/dto/response/IapProductResponse.java \
  src/main/java/com/lemon/music/musicbackservice/iap/controller/IapController.java \
  src/test/java/com/lemon/music/musicbackservice/iap/service/IapProductQueryIntegrationTest.java
git commit -m "feat(iap): iap_product 数据层 + 商品查询接口"
```

---

### Task 6: iap_order 数据层 + IapOrderService + 预下单接口

**Files:**
- Modify: `src/main/resources/schema.sql`（加 iap_order 表）
- Create: `iap/domain/IapOrderEntity.java`、`iap/mapper/IapOrderMapper.java`、`iap/service/IapOrderService.java`、`iap/dto/request/CreateOrderRequest.java`、`iap/dto/response/PreOrderResponse.java`
- Modify: `iap/controller/IapController.java`（加 `POST /orders`）
- Test: `src/test/java/com/lemon/music/musicbackservice/iap/service/IapOrderServiceIntegrationTest.java`

**Interfaces:**
- Consumes: `IapProductService.getById(Long)`（校验商品）、`AuthContext.get()`（取 userId）
- Produces: `IapOrderEntity`、`IapOrderMapper`（`insert`/`findByOrderNo`/`updateFulfillment`）、`IapOrderService.createOrder(Long userId, Long iapProductId) → PreOrderResponse`、`IapController.createOrder`。

- [ ] **Step 1: `schema.sql` 末尾加 `iap_order` 表**

```sql

-- 业务订单表（预下单落库）
CREATE TABLE IF NOT EXISTS iap_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    iap_product_id BIGINT NOT NULL,
    huawei_product_id VARCHAR(128) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
    status VARCHAR(16) NOT NULL,
    huawei_purchase_order_id VARCHAR(64) NULL,
    huawei_purchase_token VARCHAR(512) NULL,
    created_at DATETIME NOT NULL,
    paid_at DATETIME NULL,
    fulfilled_at DATETIME NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_iap_order_no UNIQUE (order_no),
    INDEX idx_iap_order_user (user_id, created_at)
);
```

- [ ] **Step 2: 创建 `IapOrderEntity.java`**

```java
package com.lemon.music.musicbackservice.iap.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class IapOrderEntity {
    private Long id;
    private String orderNo;
    private Long userId;
    private Long iapProductId;
    private String huaweiProductId;
    private BigDecimal amount;
    private String currency;
    private OrderStatus status;
    private String huaweiPurchaseOrderId;
    private String huaweiPurchaseToken;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private LocalDateTime fulfilledAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 3: 创建 `IapOrderMapper.java`**

```java
package com.lemon.music.musicbackservice.iap.mapper;

import com.lemon.music.musicbackservice.iap.domain.IapOrderEntity;
import com.lemon.music.musicbackservice.iap.domain.OrderStatus;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface IapOrderMapper {

    @Insert("INSERT INTO iap_order (order_no, user_id, iap_product_id, huawei_product_id, amount, currency, "
            + "status, huawei_purchase_order_id, huawei_purchase_token, created_at, paid_at, fulfilled_at, updated_at) "
            + "VALUES (#{orderNo}, #{userId}, #{iapProductId}, #{huaweiProductId}, #{amount}, #{currency}, "
            + "#{status}, #{huaweiPurchaseOrderId}, #{huaweiPurchaseToken}, #{createdAt}, #{paidAt}, #{fulfilledAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(IapOrderEntity entity);

    @Select("SELECT id, order_no, user_id, iap_product_id, huawei_product_id, amount, currency, status, "
            + "huawei_purchase_order_id, huawei_purchase_token, created_at, paid_at, fulfilled_at, updated_at "
            + "FROM iap_order WHERE order_no = #{orderNo}")
    IapOrderEntity findByOrderNo(@Param("orderNo") String orderNo);

    @Update("UPDATE iap_order SET huawei_purchase_order_id = #{purchaseOrderId}, "
            + "huawei_purchase_token = #{purchaseToken}, status = #{status}, "
            + "paid_at = #{paidAt}, fulfilled_at = #{fulfilledAt}, updated_at = #{updatedAt} "
            + "WHERE id = #{id}")
    int updateFulfillment(@Param("id") Long id,
                          @Param("purchaseOrderId") String purchaseOrderId,
                          @Param("purchaseToken") String purchaseToken,
                          @Param("status") OrderStatus status,
                          @Param("paidAt") LocalDateTime paidAt,
                          @Param("fulfilledAt") LocalDateTime fulfilledAt,
                          @Param("updatedAt") LocalDateTime updatedAt);
}
```

- [ ] **Step 4: 创建 DTO `CreateOrderRequest.java` 与 `PreOrderResponse.java`**

```java
package com.lemon.music.musicbackservice.iap.dto.request;

import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(
        @NotNull(message = "IAP 商品ID不能为空")
        Long iapProductId
) {}
```

```java
package com.lemon.music.musicbackservice.iap.dto.response;

import com.lemon.music.musicbackservice.iap.domain.IapProductType;

import java.math.BigDecimal;

public record PreOrderResponse(
        String orderNo,
        String huaweiProductId,
        IapProductType iapProductType,
        BigDecimal amount,
        String currency
) {}
```

- [ ] **Step 5: 创建 `IapOrderService.java`**

```java
package com.lemon.music.musicbackservice.iap.service;

import com.lemon.music.musicbackservice.iap.domain.IapOrderEntity;
import com.lemon.music.musicbackservice.iap.domain.IapProductEntity;
import com.lemon.music.musicbackservice.iap.domain.OrderStatus;
import com.lemon.music.musicbackservice.iap.dto.response.PreOrderResponse;
import com.lemon.music.musicbackservice.iap.mapper.IapOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IapOrderService {

    private final IapOrderMapper iapOrderMapper;
    private final IapProductService iapProductService;

    /** 预下单：初始化业务订单，返回订单号作为端侧 developerPayload。 */
    @Transactional
    public PreOrderResponse createOrder(Long userId, Long iapProductId) {
        IapProductEntity product = iapProductService.getById(iapProductId);
        LocalDateTime now = LocalDateTime.now();

        IapOrderEntity order = new IapOrderEntity();
        order.setOrderNo("IAP" + UUID.randomUUID().toString().replace("-", ""));
        order.setUserId(userId);
        order.setIapProductId(product.getId());
        order.setHuaweiProductId(product.getHuaweiProductId());
        order.setAmount(product.getPrice());
        order.setCurrency(product.getCurrency());
        order.setStatus(OrderStatus.PENDING);
        order.setHuaweiPurchaseOrderId(null);
        order.setHuaweiPurchaseToken(null);
        order.setCreatedAt(now);
        order.setPaidAt(null);
        order.setFulfilledAt(null);
        order.setUpdatedAt(now);
        iapOrderMapper.insert(order);

        return new PreOrderResponse(order.getOrderNo(), product.getHuaweiProductId(),
                product.getIapProductType(), product.getPrice(), product.getCurrency());
    }
}
```

- [ ] **Step 6: `IapController.java` 加预下单端点**

在 `IapController` 加字段 `private final IapOrderService iapOrderService;`（更新 `@RequiredArgsConstructor` 字段列表），并新增方法：

```java
    /** 预下单（登录，需 IAP_PURCHASE 权限）。 */
    @PostMapping("/orders")
    @com.lemon.music.musicbackservice.auth.RequirePermission("IAP_PURCHASE")
    public ApiResponse<PreOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Long userId = com.lemon.music.musicbackservice.auth.AuthContext.get().userId();
        return ApiResponse.ok(iapOrderService.createOrder(userId, request.iapProductId()));
    }
```

并在文件顶部 import 补充：`jakarta.validation.Valid`、`org.springframework.web.bind.annotation.PostMapping`、`org.springframework.web.bind.annotation.RequestBody`、`CreateOrderRequest`、`PreOrderResponse`、`IapOrderService`。

- [ ] **Step 7: 写测试 `IapOrderServiceIntegrationTest.java`**

```java
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
```

- [ ] **Step 8: 运行测试，确认通过**

Run: `./mvnw test -Dtest=IapOrderServiceIntegrationTest`
Expected: PASS。

- [ ] **Step 9: 提交**

```bash
git add src/main/resources/schema.sql \
  src/main/java/com/lemon/music/musicbackservice/iap/domain/IapOrderEntity.java \
  src/main/java/com/lemon/music/musicbackservice/iap/mapper/IapOrderMapper.java \
  src/main/java/com/lemon/music/musicbackservice/iap/service/IapOrderService.java \
  src/main/java/com/lemon/music/musicbackservice/iap/dto/request/CreateOrderRequest.java \
  src/main/java/com/lemon/music/musicbackservice/iap/dto/response/PreOrderResponse.java \
  src/main/java/com/lemon/music/musicbackservice/iap/controller/IapController.java \
  src/test/java/com/lemon/music/musicbackservice/iap/service/IapOrderServiceIntegrationTest.java
git commit -m "feat(iap): iap_order 数据层 + 预下单接口"
```

---

### Task 7: MembershipService 回收方法 + IAP_REVOKE 原因

**Files:**
- Modify: `membership/domain/PointsChangeReason.java`（加 `IAP_REVOKE`）
- Modify: `membership/service/MembershipService.java`（加 `deductPoints`/`downgradeMembershipType`/`revokeSubscription`）
- Test: `src/test/java/com/lemon/music/musicbackservice/membership/MembershipRevokeIntegrationTest.java`

**Interfaces:**
- Produces: `MembershipService.deductPoints(Long userId, int points, PointsChangeReason, String desc)`、`downgradeMembershipType(Long userId, MembershipType)`、`revokeSubscription(Long userId)`。供 Task 10 回收调用。

- [ ] **Step 1: `PointsChangeReason.java` 加 `IAP_REVOKE`**

在枚举末尾 `SUBSCRIPTION_EXPIRE` 后加：
```java
    ,
    /** IAP 退款回收 */
    IAP_REVOKE
```

- [ ] **Step 2: `MembershipService.java` 加三个回收方法**

在 `upgradeMembershipType` 方法之后，加入：

```java
    /**
     * 扣减积分（IAP 退款回收等）。积分不为负，不足扣到 0。
     * 变化记录到积分历史。
     */
    public void deductPoints(Long userId, int points, PointsChangeReason reason, String description) {
        if (points <= 0) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            MembershipEntity membership = membershipMapper.findByUserId(userId);
            if (membership == null) {
                throw new BusinessException("用户会员信息不存在");
            }
            int oldPoints = membership.getCurrentPoints();
            int newPoints = Math.max(0, oldPoints - points);
            int actualChange = newPoints - oldPoints; // 负数或 0
            applyPointsChange(membership, oldPoints, newPoints, actualChange, reason, description);
        });
    }

    /** 降级会员类型（如 IAP 退款：SVIP → VIP）。已为目标类型时忽略。 */
    public void downgradeMembershipType(Long userId, MembershipType newType) {
        MembershipEntity membership = getMembershipByUserId(userId);
        if (membership.getMembershipType() == newType) {
            return;
        }
        membership.setMembershipType(newType);
        membership.setUpdatedAt(LocalDateTime.now());
        membershipMapper.update(membership);
    }

    /** 撤销订阅资格（IAP 退款/订阅过期）：置 has_membership=false 并清到期时间。 */
    public void revokeSubscription(Long userId) {
        MembershipEntity membership = getMembershipByUserId(userId);
        membership.setHasMembership(false);
        membership.setSubscriptionExpireAt(null);
        membership.setUpdatedAt(LocalDateTime.now());
        membershipMapper.update(membership);
    }
```

> `applyPointsChange` 已存在且会按 newPoints 重算等级——退款回收后积分下降会自动降级 VIP 等级，符合预期。

- [ ] **Step 3: 写测试 `MembershipRevokeIntegrationTest.java`**

```java
package com.lemon.music.musicbackservice.membership;

import com.lemon.music.musicbackservice.membership.domain.MembershipEntity;
import com.lemon.music.musicbackservice.membership.domain.MembershipType;
import com.lemon.music.musicbackservice.membership.domain.PointsChangeReason;
import com.lemon.music.musicbackservice.membership.mapper.MembershipMapper;
import com.lemon.music.musicbackservice.membership.service.MembershipService;
import com.lemon.music.musicbackservice.user.dto.RegisterRequest;
import com.lemon.music.musicbackservice.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class MembershipRevokeIntegrationTest {

    @MockitoBean
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    private MembershipService membershipService;
    @Autowired
    private UserService userService;
    @Autowired
    private MembershipMapper membershipMapper;

    @Test
    void deductPointsStopsAtZero() {
        Long userId = userService.register(new RegisterRequest("revoke1", "pw", null));
        membershipService.addPoints(userId, 100, PointsChangeReason.PRODUCT_REDEEM, "grant");

        membershipService.deductPoints(userId, 1000, PointsChangeReason.IAP_REVOKE, "refund");

        MembershipEntity m = membershipMapper.findByUserId(userId);
        assertThat(m.getCurrentPoints()).isZero();
    }

    @Test
    void downgradeFromSvipToVip() {
        Long userId = userService.register(new RegisterRequest("revoke2", "pw", null));
        membershipService.upgradeMembershipType(userId, MembershipType.SVIP);

        membershipService.downgradeMembershipType(userId, MembershipType.VIP);

        assertThat(membershipMapper.findByUserId(userId).getMembershipType()).isEqualTo(MembershipType.VIP);
    }

    @Test
    void revokeSubscriptionClearsMembership() {
        Long userId = userService.register(new RegisterRequest("revoke3", "pw", null));
        membershipService.purchaseSubscription(userId,
                com.lemon.music.musicbackservice.membership.domain.SubscriptionType.MONTHLY);

        membershipService.revokeSubscription(userId);

        MembershipEntity m = membershipMapper.findByUserId(userId);
        assertThat(m.getHasMembership()).isFalse();
        assertThat(m.getSubscriptionExpireAt()).isNull();
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

Run: `./mvnw test -Dtest=MembershipRevokeIntegrationTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/lemon/music/musicbackservice/membership/domain/PointsChangeReason.java \
  src/main/java/com/lemon/music/musicbackservice/membership/service/MembershipService.java \
  src/test/java/com/lemon/music/musicbackservice/membership/MembershipRevokeIntegrationTest.java
git commit -m "feat(membership): IAP 退款回收方法 + IAP_REVOKE 原因"
```

---

### Task 8: iap_fulfillment 数据层

**Files:**
- Modify: `src/main/resources/schema.sql`（加 iap_fulfillment 表）
- Create: `iap/domain/IapFulfillmentEntity.java`、`iap/mapper/IapFulfillmentMapper.java`
- Test: `src/test/java/com/lemon/music/musicbackservice/iap/mapper/IapFulfillmentMapperTest.java`

**Interfaces:**
- Produces: `IapFulfillmentEntity`、`IapFulfillmentMapper`（`insert`/`findByPurchaseOrderIdAndAction`/`findGrantByPurchaseOrderId`）。供 Task 10 使用。

- [ ] **Step 1: `schema.sql` 末尾加 `iap_fulfillment` 表**

```sql

-- 权益发放记录表（防重复发货/重复回收）
CREATE TABLE IF NOT EXISTS iap_fulfillment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    huawei_purchase_order_id VARCHAR(64) NOT NULL,
    huawei_purchase_token VARCHAR(512) NULL,
    iap_order_id BIGINT NULL,
    user_id BIGINT NOT NULL,
    iap_product_id BIGINT NOT NULL,
    iap_product_type VARCHAR(24) NOT NULL,
    action VARCHAR(16) NOT NULL,
    points_granted INT NOT NULL DEFAULT 0,
    effect_snapshot TEXT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT uk_iap_fulfillment_po_action UNIQUE (huawei_purchase_order_id, action)
);
```

- [ ] **Step 2: 创建 `IapFulfillmentEntity.java`**

```java
package com.lemon.music.musicbackservice.iap.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IapFulfillmentEntity {
    private Long id;
    private String huaweiPurchaseOrderId;
    private String huaweiPurchaseToken;
    private Long iapOrderId;
    private Long userId;
    private Long iapProductId;
    private IapProductType iapProductType;
    private FulfillmentAction action;
    private Integer pointsGranted;
    private String effectSnapshot;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 3: 创建 `IapFulfillmentMapper.java`**

```java
package com.lemon.music.musicbackservice.iap.mapper;

import com.lemon.music.musicbackservice.iap.domain.FulfillmentAction;
import com.lemon.music.musicbackservice.iap.domain.IapFulfillmentEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface IapFulfillmentMapper {

    @Insert("INSERT INTO iap_fulfillment (huawei_purchase_order_id, huawei_purchase_token, iap_order_id, "
            + "user_id, iap_product_id, iap_product_type, action, points_granted, effect_snapshot, created_at) "
            + "VALUES (#{huaweiPurchaseOrderId}, #{huaweiPurchaseToken}, #{iapOrderId}, #{userId}, "
            + "#{iapProductId}, #{iapProductType}, #{action}, #{pointsGranted}, #{effectSnapshot}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(IapFulfillmentEntity entity);

    @Select("SELECT id, huawei_purchase_order_id, huawei_purchase_token, iap_order_id, user_id, iap_product_id, "
            + "iap_product_type, action, points_granted, effect_snapshot, created_at "
            + "FROM iap_fulfillment WHERE huawei_purchase_order_id = #{poId} AND action = #{action}")
    IapFulfillmentEntity findByPurchaseOrderIdAndAction(@Param("poId") String purchaseOrderId,
                                                       @Param("action") FulfillmentAction action);
}
```

- [ ] **Step 4: 写测试 `IapFulfillmentMapperTest.java`**

```java
package com.lemon.music.musicbackservice.iap.mapper;

import com.lemon.music.musicbackservice.iap.domain.FulfillmentAction;
import com.lemon.music.musicbackservice.iap.domain.IapFulfillmentEntity;
import com.lemon.music.musicbackservice.iap.domain.IapProductType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class IapFulfillmentMapperTest {

    @MockitoBean
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IapFulfillmentMapper mapper;

    @Test
    void insertAndFindByPoAndAction() {
        IapFulfillmentEntity f = grant("PO1", 100);

        IapFulfillmentEntity found = mapper.findByPurchaseOrderIdAndAction("PO1", FulfillmentAction.GRANT);
        assertThat(found).isNotNull();
        assertThat(found.getPointsGranted()).isEqualTo(100);
        assertThat(found.getIapProductType()).isEqualTo(IapProductType.CONSUMABLE);
    }

    @Test
    void uniqueConstraintPreventsDuplicateGrant() {
        grant("PO2", 50);
        // 同 (poId, GRANT) 再次插入应触发唯一约束
        assertThatThrownBy(() -> grant("PO2", 50)).isInstanceOf(Exception.class);
    }

    private IapFulfillmentEntity grant(String poId, int points) {
        IapFulfillmentEntity f = new IapFulfillmentEntity();
        f.setHuaweiPurchaseOrderId(poId);
        f.setHuaweiPurchaseToken("T_" + poId);
        f.setUserId(1L);
        f.setIapProductId(1L);
        f.setIapProductType(IapProductType.CONSUMABLE);
        f.setAction(FulfillmentAction.GRANT);
        f.setPointsGranted(points);
        f.setEffectSnapshot("{}");
        f.setCreatedAt(LocalDateTime.now());
        mapper.insert(f);
        return f;
    }
}
```

- [ ] **Step 5: 运行测试，确认通过**

Run: `./mvnw test -Dtest=IapFulfillmentMapperTest`
Expected: PASS（插入查询 + 唯一约束防重复）。

- [ ] **Step 6: 提交**

```bash
git add src/main/resources/schema.sql \
  src/main/java/com/lemon/music/musicbackservice/iap/domain/IapFulfillmentEntity.java \
  src/main/java/com/lemon/music/musicbackservice/iap/mapper/IapFulfillmentMapper.java \
  src/test/java/com/lemon/music/musicbackservice/iap/mapper/IapFulfillmentMapperTest.java
git commit -m "feat(iap): iap_fulfillment 数据层 + 唯一约束幂等"
```


### Task 9: IapHuaweiClient（调用华为 REST API）

**Files:**
- Create: `iap/dto/response/HuaweiOrderStatusResponse.java`、`HuaweiSubStatusResponse.java`、`HuaweiSimpleResponse.java`
- Create: `iap/service/IapHuaweiClient.java`
- Test: `src/test/java/com/lemon/music/musicbackservice/iap/service/IapHuaweiClientTest.java`

**Interfaces:**
- Consumes: `IapProperties.baseUrl()`、`IapJwtGenerator.generate(String)`、`RestClient.Builder`（Spring 自动配置）
- Produces: `IapHuaweiClient.orderStatusQuery(poId,token)→HuaweiOrderStatusResponse`、`orderShippedConfirm(poId,token)→boolean`、`subStatusQuery(poId,token)→HuaweiSubStatusResponse`、`subShippedConfirm(poId,token)→boolean`。

- [ ] **Step 1: 创建三个响应 record**

`HuaweiOrderStatusResponse.java`：
```java
package com.lemon.music.musicbackservice.iap.dto.response;

public record HuaweiOrderStatusResponse(
        String responseCode,
        String responseMessage,
        String jwsPurchaseOrder
) {}
```

`HuaweiSubStatusResponse.java`：
```java
package com.lemon.music.musicbackservice.iap.dto.response;

public record HuaweiSubStatusResponse(
        String responseCode,
        String responseMessage,
        String jwsSubGroupStatus
) {}
```

`HuaweiSimpleResponse.java`（确认发货用）：
```java
package com.lemon.music.musicbackservice.iap.dto.response;

public record HuaweiSimpleResponse(
        String responseCode,
        String responseMessage
) {}
```

- [ ] **Step 2: 创建 `IapHuaweiClient.java`**

```java
package com.lemon.music.musicbackservice.iap.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemon.music.musicbackservice.common.BusinessException;
import com.lemon.music.musicbackservice.iap.dto.response.HuaweiOrderStatusResponse;
import com.lemon.music.musicbackservice.iap.dto.response.HuaweiSimpleResponse;
import com.lemon.music.musicbackservice.iap.dto.response.HuaweiSubStatusResponse;
import com.lemon.music.musicbackservice.iap.support.IapJwtGenerator;
import com.lemon.music.musicbackservice.iap.support.IapProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** 调用华为 IAP REST API（订单/订阅 查询、确认发货），请求带 ES256 JWT。 */
@Slf4j
@Component
public class IapHuaweiClient {

    private static final String URL_ORDER_STATUS_QUERY = "/order/harmony/v1/application/order/status/query";
    private static final String URL_ORDER_SHIPPED_CONFIRM = "/order/harmony/v1/application/purchase/shipped/confirm";
    private static final String URL_SUB_STATUS_QUERY = "/subscription/harmony/v1/application/subscription/status/query";
    private static final String URL_SUB_SHIPPED_CONFIRM = "/subscription/harmony/v1/application/purchase/shipped/confirm";

    private final RestClient restClient;
    private final IapJwtGenerator jwtGenerator;
    private final ObjectMapper objectMapper;

    public IapHuaweiClient(RestClient.Builder restClientBuilder,
                           IapProperties properties,
                           IapJwtGenerator jwtGenerator,
                           ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.jwtGenerator = jwtGenerator;
        this.objectMapper = objectMapper;
    }

    public HuaweiOrderStatusResponse orderStatusQuery(String purchaseOrderId, String purchaseToken) {
        return post(URL_ORDER_STATUS_QUERY, Map.of("purchaseOrderId", purchaseOrderId, "purchaseToken", purchaseToken),
                HuaweiOrderStatusResponse.class);
    }

    public boolean orderShippedConfirm(String purchaseOrderId, String purchaseToken) {
        HuaweiSimpleResponse r = post(URL_ORDER_SHIPPED_CONFIRM,
                Map.of("purchaseOrderId", purchaseOrderId, "purchaseToken", purchaseToken), HuaweiSimpleResponse.class);
        return "0".equals(r.responseCode());
    }

    public HuaweiSubStatusResponse subStatusQuery(String purchaseOrderId, String purchaseToken) {
        return post(URL_SUB_STATUS_QUERY, Map.of("purchaseOrderId", purchaseOrderId, "purchaseToken", purchaseToken),
                HuaweiSubStatusResponse.class);
    }

    public boolean subShippedConfirm(String purchaseOrderId, String purchaseToken) {
        HuaweiSimpleResponse r = post(URL_SUB_SHIPPED_CONFIRM,
                Map.of("purchaseOrderId", purchaseOrderId, "purchaseToken", purchaseToken), HuaweiSimpleResponse.class);
        return "0".equals(r.responseCode());
    }

    private <T> T post(String path, Map<String, Object> body, Class<T> type) {
        String bodyJson;
        try {
            bodyJson = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new BusinessException("构造华为请求体失败");
        }
        String jwt = jwtGenerator.generate(bodyJson);
        try {
            return restClient.post()
                    .uri(path)
                    .header("Authorization", "Bearer " + jwt)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .body(bodyJson)
                    .retrieve()
                    .body(type);
        } catch (Exception e) {
            log.error("调用华为 IAP REST 失败: {}", path, e);
            throw new BusinessException("调用华为 IAP 接口失败: " + path);
        }
    }
}
```

- [ ] **Step 3: 写测试 `IapHuaweiClientTest.java`（手动构造 + MockRestServiceServer）**

```java
package com.lemon.music.musicbackservice.iap.service;

import com.lemon.music.musicbackservice.iap.dto.response.HuaweiOrderStatusResponse;
import com.lemon.music.musicbackservice.iap.support.IapJwtGenerator;
import com.lemon.music.musicbackservice.iap.support.IapProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
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
```

- [ ] **Step 4: 运行测试，确认通过**

Run: `./mvnw test -Dtest=IapHuaweiClientTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/lemon/music/musicbackservice/iap/dto/response/HuaweiOrderStatusResponse.java \
  src/main/java/com/lemon/music/musicbackservice/iap/dto/response/HuaweiSubStatusResponse.java \
  src/main/java/com/lemon/music/musicbackservice/iap/dto/response/HuaweiSimpleResponse.java \
  src/main/java/com/lemon/music/musicbackservice/iap/service/IapHuaweiClient.java \
  src/test/java/com/lemon/music/musicbackservice/iap/service/IapHuaweiClientTest.java
git commit -m "feat(iap): 华为 REST 客户端（订单/订阅 查询与确认发货）"
```

---

### Task 10: IapFulfillmentService（发放/回收/上报）+ 上报接口

**Files:**
- Modify: `iap/mapper/IapOrderMapper.java`（加 `updateStatus`）
- Create: `iap/service/IapFulfillmentService.java`、`iap/dto/request/ReportPurchaseRequest.java`、`iap/dto/response/ReportPurchaseResponse.java`
- Modify: `iap/controller/IapController.java`（加 `POST /orders/report`）
- Test: `src/test/java/com/lemon/music/musicbackservice/iap/service/IapFulfillmentServiceIntegrationTest.java`

**Interfaces:**
- Consumes: `JwsVerifier.checkAndDecode`、`MembershipService`（发放/回收）、`IapOrderMapper`/`IapFulfillmentMapper`/`IapProductMapper`、`membership.mapper.ProductMapper`（读 effect_config）
- Produces: `IapFulfillmentService.handleReport(userId, type, purchaseData)→ReportPurchaseResponse`、`grantFromOrderPayload(PurchaseOrderPayload)→boolean`、`revokeByPurchaseOrder(PurchaseOrderPayload)`。供 Task 11 复用。

- [ ] **Step 1: `IapOrderMapper.java` 加 `updateStatus`**

在接口内追加：
```java
    @Update("UPDATE iap_order SET status = #{status}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id,
                     @Param("status") OrderStatus status,
                     @Param("updatedAt") LocalDateTime updatedAt);
```

- [ ] **Step 2: 创建 DTO `ReportPurchaseRequest.java` 与 `ReportPurchaseResponse.java`**

```java
package com.lemon.music.musicbackservice.iap.dto.request;

import com.lemon.music.musicbackservice.iap.domain.IapProductType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReportPurchaseRequest(
        @NotNull(message = "商品类型不能为空")
        IapProductType iapProductType,
        @NotBlank(message = "purchaseData 不能为空")
        String purchaseData
) {}
```

```java
package com.lemon.music.musicbackservice.iap.dto.response;

public record ReportPurchaseResponse(
        boolean fulfilled,
        Long orderId,
        String message
) {}
```

- [ ] **Step 3: 创建 `IapFulfillmentService.java`**

```java
package com.lemon.music.musicbackservice.iap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemon.music.musicbackservice.common.BusinessException;
import com.lemon.music.musicbackservice.iap.domain.FulfillmentAction;
import com.lemon.music.musicbackservice.iap.domain.IapFulfillmentEntity;
import com.lemon.music.musicbackservice.iap.domain.IapOrderEntity;
import com.lemon.music.musicbackservice.iap.domain.IapProductEntity;
import com.lemon.music.musicbackservice.iap.domain.IapProductType;
import com.lemon.music.musicbackservice.iap.domain.OrderStatus;
import com.lemon.music.musicbackservice.iap.dto.payload.PurchaseData;
import com.lemon.music.musicbackservice.iap.dto.payload.PurchaseOrderPayload;
import com.lemon.music.musicbackservice.iap.dto.payload.SubGroupStatusPayload;
import com.lemon.music.musicbackservice.iap.dto.response.ReportPurchaseResponse;
import com.lemon.music.musicbackservice.iap.mapper.IapFulfillmentMapper;
import com.lemon.music.musicbackservice.iap.mapper.IapOrderMapper;
import com.lemon.music.musicbackservice.iap.mapper.IapProductMapper;
import com.lemon.music.musicbackservice.iap.support.JwsVerifier;
import com.lemon.music.musicbackservice.membership.domain.MembershipType;
import com.lemon.music.musicbackservice.membership.domain.PointsChangeReason;
import com.lemon.music.musicbackservice.membership.domain.ProductEntity;
import com.lemon.music.musicbackservice.membership.mapper.ProductMapper;
import com.lemon.music.musicbackservice.membership.service.MembershipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 验签发货 / 幂等 / 退款回收 —— IAP 的核心翻译层。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IapFulfillmentService {

    private final JwsVerifier jwsVerifier;
    private final ObjectMapper objectMapper;
    private final IapOrderMapper iapOrderMapper;
    private final IapFulfillmentMapper fulfillmentMapper;
    private final IapProductMapper iapProductMapper;
    private final ProductMapper productMapper;
    private final MembershipService membershipService;

    // ==================== 客户端上报（购买 + 补单）====================

    @Transactional
    public ReportPurchaseResponse handleReport(Long userId, IapProductType type, String purchaseData) {
        PurchaseData data = parse(purchaseData, PurchaseData.class);
        PurchaseOrderPayload order = resolveOrderPayload(type, data);

        IapOrderEntity iapOrder = iapOrderMapper.findByOrderNo(order.developerPayload());
        if (iapOrder == null || !iapOrder.getUserId().equals(userId)) {
            throw new BusinessException("上报数据与业务订单不匹配");
        }
        boolean fulfilled = grantFromOrderPayload(order);
        return new ReportPurchaseResponse(fulfilled, iapOrder.getId(),
                fulfilled ? "发货成功" : "无需重复发货");
    }

    // ==================== 发放（购买/补单/新交易通知 共用）====================

    @Transactional
    public boolean grantFromOrderPayload(PurchaseOrderPayload order) {
        if (isBlank(order.developerPayload())) {
            log.warn("grant: developerPayload 为空，purchaseOrderId={}", order.purchaseOrderId());
            return false;
        }
        if (fulfillmentMapper.findByPurchaseOrderIdAndAction(order.purchaseOrderId(), FulfillmentAction.GRANT) != null) {
            log.info("grant: 已发放，跳过 purchaseOrderId={}", order.purchaseOrderId());
            return false;
        }
        if (!isBlank(order.purchaseOrderRevocationReasonCode())) {
            log.info("grant: 订单已退款，不发货 purchaseOrderId={}", order.purchaseOrderId());
            return false;
        }
        IapOrderEntity iapOrder = iapOrderMapper.findByOrderNo(order.developerPayload());
        if (iapOrder == null) {
            log.warn("grant: 业务订单不存在 orderNo={}", order.developerPayload());
            return false;
        }
        IapProductEntity product = iapProductMapper.findById(iapOrder.getIapProductId());
        int pointsGranted = grantEntitlement(iapOrder.getUserId(), product);

        IapFulfillmentEntity f = new IapFulfillmentEntity();
        f.setHuaweiPurchaseOrderId(order.purchaseOrderId());
        f.setHuaweiPurchaseToken(order.purchaseToken());
        f.setIapOrderId(iapOrder.getId());
        f.setUserId(iapOrder.getUserId());
        f.setIapProductId(product.getId());
        f.setIapProductType(product.getIapProductType());
        f.setAction(FulfillmentAction.GRANT);
        f.setPointsGranted(pointsGranted);
        f.setEffectSnapshot(productEffectSnapshot(product));
        f.setCreatedAt(LocalDateTime.now());
        fulfillmentMapper.insert(f);

        LocalDateTime now = LocalDateTime.now();
        iapOrderMapper.updateFulfillment(iapOrder.getId(), order.purchaseOrderId(), order.purchaseToken(),
                OrderStatus.FULFILLED, now, now, now);
        return true;
    }

    // ==================== 回收（退款通知 共用）====================

    @Transactional
    public void revokeByPurchaseOrder(PurchaseOrderPayload order) {
        IapFulfillmentEntity grant =
                fulfillmentMapper.findByPurchaseOrderIdAndAction(order.purchaseOrderId(), FulfillmentAction.GRANT);
        if (grant == null) {
            log.warn("revoke: 无发放记录，跳过 purchaseOrderId={}", order.purchaseOrderId());
            return;
        }
        if (fulfillmentMapper.findByPurchaseOrderIdAndAction(order.purchaseOrderId(), FulfillmentAction.REVOKE) != null) {
            log.info("revoke: 已回收，跳过 purchaseOrderId={}", order.purchaseOrderId());
            return;
        }
        IapProductEntity product = iapProductMapper.findById(grant.getIapProductId());
        reverseEntitlement(grant.getUserId(), product, grant.getPointsGranted());

        IapFulfillmentEntity r = new IapFulfillmentEntity();
        r.setHuaweiPurchaseOrderId(order.purchaseOrderId());
        r.setHuaweiPurchaseToken(order.purchaseToken());
        r.setIapOrderId(grant.getIapOrderId());
        r.setUserId(grant.getUserId());
        r.setIapProductId(product.getId());
        r.setIapProductType(product.getIapProductType());
        r.setAction(FulfillmentAction.REVOKE);
        r.setPointsGranted(0);
        r.setEffectSnapshot(productEffectSnapshot(product));
        r.setCreatedAt(LocalDateTime.now());
        fulfillmentMapper.insert(r);

        iapOrderMapper.updateStatus(grant.getIapOrderId(), OrderStatus.REFUNDED, LocalDateTime.now());
    }

    // ==================== 内部：凭证解析 / 发放 / 回收 ====================

    private PurchaseOrderPayload resolveOrderPayload(IapProductType type, PurchaseData data) {
        try {
            if (type == IapProductType.AUTORENEWABLE) {
                String json = jwsVerifier.checkAndDecode(data.jwsSubscriptionStatus());
                SubGroupStatusPayload sub = objectMapper.readValue(json, SubGroupStatusPayload.class);
                if (sub.lastSubscriptionStatus() == null
                        || sub.lastSubscriptionStatus().lastPurchaseOrder() == null) {
                    throw new BusinessException("订阅状态数据缺失");
                }
                return sub.lastSubscriptionStatus().lastPurchaseOrder();
            }
            String json = jwsVerifier.checkAndDecode(data.jwsPurchaseOrder());
            return objectMapper.readValue(json, PurchaseOrderPayload.class);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("PurchaseData 解析/验签失败");
        }
    }

    private int grantEntitlement(Long userId, IapProductEntity product) {
        return switch (product.getIapProductType()) {
            case CONSUMABLE, NON_CONSUMABLE -> applyProductEffect(userId, product);
            case NONRENEWABLE, AUTORENEWABLE -> {
                membershipService.purchaseSubscription(userId, product.getSubscriptionType());
                yield 0;
            }
        };
    }

    private void reverseEntitlement(Long userId, IapProductEntity product, int pointsGranted) {
        switch (product.getIapProductType()) {
            case CONSUMABLE, NON_CONSUMABLE -> {
                ProductEntity p = productMapper.findById(product.getInternalRefId());
                String effectType = parse(p.getEffectConfig(), JsonNode.class).path("type").asText("");
                if ("POINTS_BONUS".equals(effectType)) {
                    membershipService.deductPoints(userId, pointsGranted, PointsChangeReason.IAP_REVOKE, "IAP退款回收");
                } else if ("MEMBERSHIP_UPGRADE".equals(effectType)) {
                    membershipService.downgradeMembershipType(userId, MembershipType.VIP);
                }
            }
            case NONRENEWABLE, AUTORENEWABLE -> membershipService.revokeSubscription(userId);
        }
    }

    private int applyProductEffect(Long userId, IapProductEntity iapProduct) {
        ProductEntity product = productMapper.findById(iapProduct.getInternalRefId());
        JsonNode cfg = parse(product.getEffectConfig(), JsonNode.class);
        String type = cfg.path("type").asText("");
        return switch (type) {
            case "POINTS_BONUS" -> {
                int pts = cfg.path("points").asInt(0);
                if (pts <= 0) {
                    throw new BusinessException("商品积分配置无效");
                }
                membershipService.addPoints(userId, pts, PointsChangeReason.PRODUCT_REDEEM,
                        "IAP购买:" + product.getProductName());
                yield pts;
            }
            case "MEMBERSHIP_UPGRADE" -> {
                membershipService.upgradeMembershipType(userId,
                        MembershipType.valueOf(cfg.path("membershipType").asText("VIP")));
                yield 0;
            }
            default -> throw new BusinessException("未知商品效果: " + type);
        };
    }

    private String productEffectSnapshot(IapProductEntity product) {
        if (product.getIapProductType() == IapProductType.CONSUMABLE
                || product.getIapProductType() == IapProductType.NON_CONSUMABLE) {
            ProductEntity p = productMapper.findById(product.getInternalRefId());
            return p.getEffectConfig();
        }
        return "{\"subscriptionType\":\"" + product.getSubscriptionType() + "\"}";
    }

    private <T> T parse(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new BusinessException("JSON 解析失败: " + type.getSimpleName());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
```

- [ ] **Step 4: `IapController.java` 加上报端点**

在 `IapController` 加字段 `private final IapFulfillmentService iapFulfillmentService;`，并新增方法：

```java
    /** 上报 purchaseData，服务端验签发放权益（登录，需 IAP_PURCHASE）。购买与补单共用。 */
    @PostMapping("/orders/report")
    @com.lemon.music.musicbackservice.auth.RequirePermission("IAP_PURCHASE")
    public ApiResponse<ReportPurchaseResponse> reportPurchase(@Valid @RequestBody ReportPurchaseRequest request) {
        Long userId = com.lemon.music.musicbackservice.auth.AuthContext.get().userId();
        return ApiResponse.ok(iapFulfillmentService.handleReport(userId, request.iapProductType(), request.purchaseData()));
    }
```

补充 import：`ReportPurchaseRequest`、`ReportPurchaseResponse`、`IapFulfillmentService`。

- [ ] **Step 5: 写测试 `IapFulfillmentServiceIntegrationTest.java`（mock JwsVerifier）**

```java
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
```

- [ ] **Step 6: 运行测试，确认通过**

Run: `./mvnw test -Dtest=IapFulfillmentServiceIntegrationTest`
Expected: PASS（发放+幂等、退款订单不发货）。

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/lemon/music/musicbackservice/iap/mapper/IapOrderMapper.java \
  src/main/java/com/lemon/music/musicbackservice/iap/service/IapFulfillmentService.java \
  src/main/java/com/lemon/music/musicbackservice/iap/dto/request/ReportPurchaseRequest.java \
  src/main/java/com/lemon/music/musicbackservice/iap/dto/response/ReportPurchaseResponse.java \
  src/main/java/com/lemon/music/musicbackservice/iap/controller/IapController.java \
  src/test/java/com/lemon/music/musicbackservice/iap/service/IapFulfillmentServiceIntegrationTest.java
git commit -m "feat(iap): 验签发货/退款回收核心 + 上报接口"
```

---

### Task 11: 关键事件通知（日志表 + 服务 + 公开回调）

**Files:**
- Modify: `src/main/resources/schema.sql`（加 iap_notification_log 表）
- Create: `iap/domain/IapNotificationLogEntity.java`、`iap/mapper/IapNotificationLogMapper.java`、`iap/service/IapNotificationService.java`、`iap/dto/request/IapNotificationRequest.java`、`iap/controller/IapNotificationController.java`
- Test: `src/test/java/com/lemon/music/musicbackservice/iap/service/IapNotificationServiceIntegrationTest.java`

**Interfaces:**
- Consumes: `JwsVerifier`、`IapHuaweiClient`、`IapFulfillmentService.grantFromOrderPayload/revokeByPurchaseOrder`、`MembershipService.revokeSubscription`、`IapFulfillmentMapper`（取 userId）
- Produces: `IapNotificationService.handleNotification(String jwsNotification)`、`IapNotificationController`（公开 `POST /api/iap/notifications`）。

- [ ] **Step 1: `schema.sql` 末尾加 `iap_notification_log` 表**

```sql

-- 通知日志表（排查 + 按 notificationRequestId 幂等）
CREATE TABLE IF NOT EXISTS iap_notification_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    notification_request_id VARCHAR(128) NOT NULL,
    notification_type VARCHAR(48) NOT NULL,
    notification_subtype VARCHAR(64) NULL,
    huawei_purchase_order_id VARCHAR(64) NULL,
    huawei_purchase_token VARCHAR(512) NULL,
    huawei_product_id VARCHAR(128) NULL,
    user_id BIGINT NULL,
    raw_jws TEXT NULL,
    status VARCHAR(16) NOT NULL,
    error_message VARCHAR(512) NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT uk_iap_notification_req UNIQUE (notification_request_id)
);
```

- [ ] **Step 2: 创建 `IapNotificationLogEntity.java`**

```java
package com.lemon.music.musicbackservice.iap.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IapNotificationLogEntity {
    private Long id;
    private String notificationRequestId;
    private String notificationType;
    private String notificationSubtype;
    private String huaweiPurchaseOrderId;
    private String huaweiPurchaseToken;
    private String huaweiProductId;
    private Long userId;
    private String rawJws;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 3: 创建 `IapNotificationLogMapper.java`**

```java
package com.lemon.music.musicbackservice.iap.mapper;

import com.lemon.music.musicbackservice.iap.domain.IapNotificationLogEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IapNotificationLogMapper {

    @Insert("INSERT INTO iap_notification_log (notification_request_id, notification_type, notification_subtype, "
            + "huawei_purchase_order_id, huawei_purchase_token, huawei_product_id, user_id, raw_jws, status, "
            + "error_message, created_at) "
            + "VALUES (#{notificationRequestId}, #{notificationType}, #{notificationSubtype}, "
            + "#{huaweiPurchaseOrderId}, #{huaweiPurchaseToken}, #{huaweiProductId}, #{userId}, #{rawJws}, "
            + "#{status}, #{errorMessage}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(IapNotificationLogEntity entity);

    @Select("SELECT id, notification_request_id, notification_type, notification_subtype, "
            + "huawei_purchase_order_id, huawei_purchase_token, huawei_product_id, user_id, raw_jws, status, "
            + "error_message, created_at FROM iap_notification_log WHERE notification_request_id = #{requestId}")
    IapNotificationLogEntity findByRequestId(@Param("requestId") String requestId);

    @Update("UPDATE iap_notification_log SET status = #{status}, error_message = #{errorMessage} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("errorMessage") String errorMessage);
}
```

- [ ] **Step 4: 创建 `IapNotificationRequest.java`**

```java
package com.lemon.music.musicbackservice.iap.dto.request;

import jakarta.validation.constraints.NotBlank;

public record IapNotificationRequest(
        @NotBlank(message = "jwsNotification 不能为空")
        String jwsNotification
) {}
```

- [ ] **Step 5: 创建 `IapNotificationService.java`**

```java
package com.lemon.music.musicbackservice.iap.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemon.music.musicbackservice.common.BusinessException;
import com.lemon.music.musicbackservice.iap.domain.IapFulfillmentEntity;
import com.lemon.music.musicbackservice.iap.domain.IapNotificationLogEntity;
import com.lemon.music.musicbackservice.iap.domain.IapProductType;
import com.lemon.music.musicbackservice.iap.domain.FulfillmentAction;
import com.lemon.music.musicbackservice.iap.dto.payload.NotificationPayload;
import com.lemon.music.musicbackservice.iap.dto.payload.PurchaseOrderPayload;
import com.lemon.music.musicbackservice.iap.dto.payload.SubGroupStatusPayload;
import com.lemon.music.musicbackservice.iap.dto.payload.NotificationMetaData;
import com.lemon.music.musicbackservice.iap.mapper.IapFulfillmentMapper;
import com.lemon.music.musicbackservice.iap.mapper.IapNotificationLogMapper;
import com.lemon.music.musicbackservice.iap.support.JwsVerifier;
import com.lemon.music.musicbackservice.membership.service.MembershipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 关键事件通知：JWS 验签 + 幂等 + 按类型分发。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IapNotificationService {

    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private final JwsVerifier jwsVerifier;
    private final ObjectMapper objectMapper;
    private final IapNotificationLogMapper notificationLogMapper;
    private final IapFulfillmentMapper fulfillmentMapper;
    private final IapFulfillmentService fulfillmentService;
    private final IapHuaweiClient huaweiClient;
    private final MembershipService membershipService;

    public void handleNotification(String jwsNotification) {
        NotificationPayload payload;
        try {
            String json = jwsVerifier.checkAndDecode(jwsNotification);
            payload = objectMapper.readValue(json, NotificationPayload.class);
        } catch (Exception e) {
            log.error("通知验签/解析失败", e);
            return; // 验签失败不落库，直接返回 200
        }

        // 幂等：先登记日志（唯一约束兜底重复投递）
        if (notificationLogMapper.findByRequestId(payload.notificationRequestId()) != null) {
            log.info("通知已处理，跳过 requestId={}", payload.notificationRequestId());
            return;
        }
        IapNotificationLogEntity logRow = toLogEntity(payload, jwsNotification, STATUS_PROCESSING);
        try {
            notificationLogMapper.insert(logRow);
        } catch (Exception dup) {
            log.info("通知重复投递，跳过 requestId={}", payload.notificationRequestId());
            return;
        }

        try {
            dispatch(payload);
            notificationLogMapper.updateStatus(logRow.getId(), STATUS_SUCCESS, null);
        } catch (Exception e) {
            log.error("通知处理失败 requestId={}", payload.notificationRequestId(), e);
            notificationLogMapper.updateStatus(logRow.getId(), STATUS_FAILED,
                    e.getMessage() == null ? "error" : e.getMessage().substring(0, Math.min(500, e.getMessage().length())));
        }
    }

    private void dispatch(NotificationPayload payload) {
        NotificationMetaData m = payload.notificationMetaData();
        String type = payload.notificationType();
        switch (type) {
            case "DID_NEW_TRANSACTION" -> handleNewTransaction(m);
            case "REVOKE" -> handleRevoke(m);
            case "EXPIRE" -> handleExpire(m);
            case "DID_CHANGE_RENEWAL_STATUS", "RENEWAL_TIME_MODIFIED" ->
                    log.info("通知仅记录，不改动权益: type={}", type);
            default -> log.warn("未知通知类型: {}", type);
        }
    }

    private void handleNewTransaction(NotificationMetaData m) {
        IapProductType pt = IapProductType.fromCode(m.type());
        PurchaseOrderPayload order = queryAndDecodeOrder(pt, m.purchaseOrderId(), m.purchaseToken());
        boolean granted = fulfillmentService.grantFromOrderPayload(order);
        if (granted) {
            if (pt == IapProductType.AUTORENEWABLE) {
                huaweiClient.subShippedConfirm(order.purchaseOrderId(), order.purchaseToken());
            } else {
                huaweiClient.orderShippedConfirm(order.purchaseOrderId(), order.purchaseToken());
            }
        }
    }

    private void handleRevoke(NotificationMetaData m) {
        IapProductType pt = IapProductType.fromCode(m.type());
        PurchaseOrderPayload order = queryAndDecodeOrder(pt, m.purchaseOrderId(), m.purchaseToken());
        fulfillmentService.revokeByPurchaseOrder(order);
    }

    private void handleExpire(NotificationMetaData m) {
        IapFulfillmentEntity grant = fulfillmentMapper.findByPurchaseOrderIdAndAction(
                m.purchaseOrderId(), FulfillmentAction.GRANT);
        if (grant != null) {
            membershipService.revokeSubscription(grant.getUserId());
        }
    }

    private PurchaseOrderPayload queryAndDecodeOrder(IapProductType pt, String purchaseOrderId, String purchaseToken) {
        try {
            if (pt == IapProductType.AUTORENEWABLE) {
                var resp = huaweiClient.subStatusQuery(purchaseOrderId, purchaseToken);
                String json = jwsVerifier.checkAndDecode(resp.jwsSubGroupStatus());
                SubGroupStatusPayload sub = objectMapper.readValue(json, SubGroupStatusPayload.class);
                return sub.lastSubscriptionStatus().lastPurchaseOrder();
            }
            var resp = huaweiClient.orderStatusQuery(purchaseOrderId, purchaseToken);
            String json = jwsVerifier.checkAndDecode(resp.jwsPurchaseOrder());
            return objectMapper.readValue(json, PurchaseOrderPayload.class);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("查询/验签订单状态失败");
        }
    }

    private IapNotificationLogEntity toLogEntity(NotificationPayload p, String rawJws, String status) {
        IapNotificationLogEntity e = new IapNotificationLogEntity();
        e.setNotificationRequestId(p.notificationRequestId());
        e.setNotificationType(p.notificationType());
        e.setNotificationSubtype(p.notificationSubtype());
        NotificationMetaData m = p.notificationMetaData();
        if (m != null) {
            e.setHuaweiPurchaseOrderId(m.purchaseOrderId());
            e.setHuaweiPurchaseToken(m.purchaseToken());
            e.setHuaweiProductId(m.currentProductId());
        }
        e.setRawJws(rawJws);
        e.setStatus(status);
        e.setCreatedAt(LocalDateTime.now());
        return e;
    }
}
```

- [ ] **Step 6: 创建 `IapNotificationController.java`**

```java
package com.lemon.music.musicbackservice.iap.controller;

import com.lemon.music.musicbackservice.common.ApiResponse;
import com.lemon.music.musicbackservice.iap.dto.request.IapNotificationRequest;
import com.lemon.music.musicbackservice.iap.service.IapNotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 华为关键事件通知回调（公开，路径已在 AuthInterceptor 白名单）。 */
@RestController
@RequestMapping("/api/iap")
@RequiredArgsConstructor
public class IapNotificationController {

    private final IapNotificationService iapNotificationService;

    @PostMapping("/notifications")
    public ApiResponse<Void> notify(@Valid @RequestBody IapNotificationRequest request) {
        iapNotificationService.handleNotification(request.jwsNotification());
        return ApiResponse.ok("ok", null);
    }
}
```

- [ ] **Step 7: 写测试 `IapNotificationServiceIntegrationTest.java`（mock JwsVerifier + IapHuaweiClient）**

```java
package com.lemon.music.musicbackservice.iap.service;

import com.lemon.music.musicbackservice.iap.domain.FulfillmentAction;
import com.lemon.music.musicbackservice.iap.domain.IapProductEntity;
import com.lemon.music.musicbackservice.iap.dto.response.HuaweiOrderStatusResponse;
import com.lemon.music.musicbackservice.iap.dto.response.PreOrderResponse;
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
class IapNotificationServiceIntegrationTest {

    @MockitoBean
    StringRedisTemplate stringRedisTemplate;
    @MockitoBean
    JwsVerifier jwsVerifier;
    @MockitoBean
    IapHuaweiClient huaweiClient;

    @Autowired
    private IapNotificationService notificationService;
    @Autowired
    private IapOrderService iapOrderService;
    @Autowired
    private IapFulfillmentService fulfillmentService;
    @Autowired
    private IapProductMapper iapProductMapper;
    @Autowired
    private IapFulfillmentMapper fulfillmentMapper;
    @Autowired
    private UserService userService;
    @Autowired
    private MembershipMapper membershipMapper;

    @Test
    void revokeNotificationRecallsPoints() throws Exception {
        // 1. 预下单 + 上报发货，获得 1000 积分
        Long userId = userService.register(new RegisterRequest("ntf1", "pw", null));
        IapProductEntity gem = iapProductMapper.findByHuaweiProductId("iap_gem_card_001");
        PreOrderResponse pre = iapOrderService.createOrder(userId, gem.getId());
        String orderPayload = "{\"productId\":\"iap_gem_card_001\",\"purchaseOrderId\":\"PO_N\","
                + "\"purchaseToken\":\"T_N\",\"developerPayload\":\"" + pre.orderNo() + "\","
                + "\"finishStatus\":\"2\",\"purchaseOrderRevocationReasonCode\":null,\"productType\":0}";
        when(jwsVerifier.checkAndDecode(anyString())).thenReturn(orderPayload);
        fulfillmentService.handleReport(userId,
                com.lemon.music.musicbackservice.iap.domain.IapProductType.CONSUMABLE,
                "{\"type\":0,\"jwsPurchaseOrder\":\"dummy\"}");
        assertThat(membershipMapper.findByUserId(userId).getCurrentPoints()).isEqualTo(1000);

        // 2. REVOKE 通知：通知 JWS 解码为 NotificationPayload；订单查询返回 JWS_PO，再解码为 orderPayload
        String notificationPayload = "{\"notificationType\":\"REVOKE\",\"notificationSubtype\":\"REFUND_TRANSACTION\","
                + "\"notificationRequestId\":\"REQ1\",\"notificationVersion\":\"v1\",\"signedTime\":1,"
                + "\"notificationMetaData\":{\"type\":0,\"currentProductId\":\"iap_gem_card_001\","
                + "\"purchaseOrderId\":\"PO_N\",\"purchaseToken\":\"T_N\"}}";
        when(jwsVerifier.checkAndDecode("NOTIFICATION_JWS")).thenReturn(notificationPayload);
        when(jwsVerifier.checkAndDecode("JWS_PO")).thenReturn(orderPayload);
        when(huaweiClient.orderStatusQuery("PO_N", "T_N"))
                .thenReturn(new HuaweiOrderStatusResponse("0", "ok", "JWS_PO"));

        notificationService.handleNotification("NOTIFICATION_JWS");

        // 3. 积分被回收，fulfillment 存在 REVOKE 记录
        assertThat(membershipMapper.findByUserId(userId).getCurrentPoints()).isZero();
        assertThat(fulfillmentMapper.findByPurchaseOrderIdAndAction("PO_N", FulfillmentAction.REVOKE)).isNotNull();
    }

    @Test
    void notificationIsIdempotent() throws Exception {
        String notificationPayload = "{\"notificationType\":\"EXPIRE\",\"notificationRequestId\":\"REQ2\","
                + "\"notificationMetaData\":{\"type\":2,\"purchaseOrderId\":\"PO_NONE\",\"purchaseToken\":\"T\"}}";
        when(jwsVerifier.checkAndDecode(anyString())).thenReturn(notificationPayload);

        notificationService.handleNotification("JWS");
        notificationService.handleNotification("JWS"); // 重复

        // 第二次因 requestId 重复被跳过，不报错
        assertThat(membershipMapper.findByUserId(999999L)).isNull();
    }
}
```

- [ ] **Step 8: 运行测试，确认通过**

Run: `./mvnw test -Dtest=IapNotificationServiceIntegrationTest`
Expected: PASS（REVOKE 回收 + 通知幂等）。

- [ ] **Step 9: 提交**

```bash
git add src/main/resources/schema.sql \
  src/main/java/com/lemon/music/musicbackservice/iap/domain/IapNotificationLogEntity.java \
  src/main/java/com/lemon/music/musicbackservice/iap/mapper/IapNotificationLogMapper.java \
  src/main/java/com/lemon/music/musicbackservice/iap/service/IapNotificationService.java \
  src/main/java/com/lemon/music/musicbackservice/iap/dto/request/IapNotificationRequest.java \
  src/main/java/com/lemon/music/musicbackservice/iap/controller/IapNotificationController.java \
  src/test/java/com/lemon/music/musicbackservice/iap/service/IapNotificationServiceIntegrationTest.java
git commit -m "feat(iap): 关键事件通知（验签+幂等+分发，含退款/过期）"
```

---

### Task 12: 删除旧购买接口 + 端到端集成测试

**Files:**
- Modify: `membership/controller/MembershipController.java`（删除 subscribe/products/purchase/products/{id}/use；移除 ProductService 依赖）
- Delete: `membership/service/ProductService.java`（旧直接发放逻辑，已被 IAP 取代）
- Delete: `membership/dto/request/PurchaseSubscriptionRequest.java`、`PurchaseProductRequest.java`（旧请求 DTO）
- Delete: `membership/dto/response/ProductInfoResponse.java`（旧商品响应）
- Test: `src/test/java/com/lemon/music/musicbackservice/iap/IapEndToEndIntegrationTest.java`

**Interfaces:**
- 移除：`POST /api/membership/subscribe`、`POST /api/membership/products`、`POST /api/membership/products/purchase`、`POST /api/membership/products/{id}/use`（购买全部走 `/api/iap/*`）。
- 保留：`MembershipController` 的 `GET /info`、`GET /points-history`；`MembershipService.purchaseSubscription`（IAP 发放订阅仍用）；`ProductMapper`（IAP 读 effect_config 仍用）。

> 注：`user_product` 表与 `UserProductMapper` 不再有写入方，保留表与 mapper 不影响运行（与遗留列 `membership_level` 同样后续手动清理）。

- [ ] **Step 1: 删除旧文件**

```bash
git rm src/main/java/com/lemon/music/musicbackservice/membership/service/ProductService.java
git rm src/main/java/com/lemon/music/musicbackservice/membership/dto/request/PurchaseSubscriptionRequest.java
git rm src/main/java/com/lemon/music/musicbackservice/membership/dto/request/PurchaseProductRequest.java
git rm src/main/java/com/lemon/music/musicbackservice/membership/dto/response/ProductInfoResponse.java
```

- [ ] **Step 2: 改写 `MembershipController.java`**

将整个文件替换为（仅保留 info、points-history；移除 ProductService、各购买/use 方法及其 import）：

```java
package com.lemon.music.musicbackservice.membership.controller;

import com.lemon.music.musicbackservice.auth.AuthContext;
import com.lemon.music.musicbackservice.common.ApiResponse;
import com.lemon.music.musicbackservice.membership.domain.MembershipEntity;
import com.lemon.music.musicbackservice.membership.domain.MembershipPointsHistoryEntity;
import com.lemon.music.musicbackservice.membership.dto.response.MembershipInfoResponse;
import com.lemon.music.musicbackservice.membership.dto.response.PointsHistoryResponse;
import com.lemon.music.musicbackservice.membership.service.MembershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 会员系统 API。购买流程已迁移至 /api/iap/*。 */
@RestController
@RequestMapping("/api/membership")
@RequiredArgsConstructor
public class MembershipController {

    private final MembershipService membershipService;

    @GetMapping("/info")
    public ApiResponse<MembershipInfoResponse> getMembershipInfo() {
        Long userId = AuthContext.get().userId();
        MembershipEntity membership = membershipService.getMembershipByUserId(userId);
        String nextLevelInfo = membershipService.calculateNextLevelInfo(
                membership.getCurrentPoints(), membership.getVipLevel());
        MembershipInfoResponse response = new MembershipInfoResponse(
                membership.getUserId(), membership.getCurrentPoints(), membership.getMembershipType(),
                membership.getVipLevel(), membership.getHasMembership(), membership.getSubscriptionExpireAt(),
                nextLevelInfo, membership.getCreatedAt(), membership.getUpdatedAt());
        return ApiResponse.ok(response);
    }

    @GetMapping("/points-history")
    public ApiResponse<List<PointsHistoryResponse>> getPointsHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = AuthContext.get().userId();
        return ApiResponse.ok(membershipService.getPointsHistory(userId, page, size).stream()
                .map(h -> new PointsHistoryResponse(h.getId(), h.getPointsChange(), h.getPointsBefore(),
                        h.getPointsAfter(), h.getChangeReason(), h.getDescription(), h.getCreatedAt()))
                .toList());
    }
}
```

- [ ] **Step 3: 写端到端测试 `IapEndToEndIntegrationTest.java`**

覆盖：预下单 → 上报发货（+1000 积分）→ REVOKE 通知回收（积分归零）→ 重复上报幂等。

```java
package com.lemon.music.musicbackservice.iap;

import com.lemon.music.musicbackservice.iap.domain.FulfillmentAction;
import com.lemon.music.musicbackservice.iap.domain.IapProductEntity;
import com.lemon.music.musicbackservice.iap.domain.IapProductType;
import com.lemon.music.musicbackservice.iap.dto.response.HuaweiOrderStatusResponse;
import com.lemon.music.musicbackservice.iap.dto.response.PreOrderResponse;
import com.lemon.music.musicbackservice.iap.dto.response.ReportPurchaseResponse;
import com.lemon.music.musicbackservice.iap.mapper.IapFulfillmentMapper;
import com.lemon.music.musicbackservice.iap.mapper.IapProductMapper;
import com.lemon.music.musicbackservice.iap.service.IapFulfillmentService;
import com.lemon.music.musicbackservice.iap.service.IapHuaweiClient;
import com.lemon.music.musicbackservice.iap.service.IapNotificationService;
import com.lemon.music.musicbackservice.iap.service.IapOrderService;
import com.lemon.music.musicbackservice.iap.support.JwsVerifier;
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
class IapEndToEndIntegrationTest {

    @MockitoBean
    StringRedisTemplate stringRedisTemplate;
    @MockitoBean
    JwsVerifier jwsVerifier;
    @MockitoBean
    IapHuaweiClient huaweiClient;

    @Autowired
    private IapOrderService orderService;
    @Autowired
    private IapFulfillmentService fulfillmentService;
    @Autowired
    private IapNotificationService notificationService;
    @Autowired
    private IapProductMapper productMapper;
    @Autowired
    private IapFulfillmentMapper fulfillmentMapper;
    @Autowired
    private UserService userService;
    @Autowired
    private MembershipMapper membershipMapper;

    @Test
    void preorderReportGrantThenRevokeRecalls() throws Exception {
        Long userId = userService.register(new RegisterRequest("e2e", "pw", null));
        IapProductEntity gem = productMapper.findByHuaweiProductId("iap_gem_card_001");

        // 预下单
        PreOrderResponse pre = orderService.createOrder(userId, gem.getId());
        assertThat(pre.huaweiProductId()).isEqualTo("iap_gem_card_001");

        // 上报发货
        String orderPayload = "{\"productId\":\"iap_gem_card_001\",\"purchaseOrderId\":\"PO_E2E\","
                + "\"purchaseToken\":\"T_E2E\",\"developerPayload\":\"" + pre.orderNo() + "\","
                + "\"finishStatus\":\"2\",\"purchaseOrderRevocationReasonCode\":null,\"productType\":0}";
        when(jwsVerifier.checkAndDecode(anyString())).thenReturn(orderPayload);
        ReportPurchaseResponse report = fulfillmentService.handleReport(userId, IapProductType.CONSUMABLE,
                "{\"type\":0,\"jwsPurchaseOrder\":\"dummy\"}");
        assertThat(report.fulfilled()).isTrue();
        assertThat(membershipMapper.findByUserId(userId).getCurrentPoints()).isEqualTo(1000);

        // REVOKE 通知回收
        String notificationPayload = "{\"notificationType\":\"REVOKE\",\"notificationSubtype\":\"REFUND_TRANSACTION\","
                + "\"notificationRequestId\":\"REQ_E2E\",\"notificationVersion\":\"v1\",\"signedTime\":1,"
                + "\"notificationMetaData\":{\"type\":0,\"currentProductId\":\"iap_gem_card_001\","
                + "\"purchaseOrderId\":\"PO_E2E\",\"purchaseToken\":\"T_E2E\"}}";
        when(jwsVerifier.checkAndDecode("NOTIF")).thenReturn(notificationPayload);
        when(jwsVerifier.checkAndDecode("JWS_PO")).thenReturn(orderPayload);
        when(huaweiClient.orderStatusQuery("PO_E2E", "T_E2E"))
                .thenReturn(new HuaweiOrderStatusResponse("0", "ok", "JWS_PO"));

        notificationService.handleNotification("NOTIF");

        assertThat(membershipMapper.findByUserId(userId).getCurrentPoints()).isZero();
        assertThat(fulfillmentMapper.findByPurchaseOrderIdAndAction("PO_E2E", FulfillmentAction.GRANT)).isNotNull();
        assertThat(fulfillmentMapper.findByPurchaseOrderIdAndAction("PO_E2E", FulfillmentAction.REVOKE)).isNotNull();
    }
}
```

- [ ] **Step 4: 运行全量测试**

Run: `./mvnw test`
Expected: 全部 PASS（含新增 IAP 测试、既有会员/用户测试；旧购买接口已移除，无残留引用编译错误）。

> 若编译报错提示 `ProductService`/旧 DTO 被引用：搜索残留引用并清除（`grep -rn "ProductService\|PurchaseProductRequest\|PurchaseSubscriptionRequest\|ProductInfoResponse" src/main`）。

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "feat(iap): 移除旧直接发放购买接口 + IAP 端到端集成测试"
```

---

## 部署前置（实现完成后）

1. 在 AppGallery Connect 开启应用内购买、配置 `client_id`，上架 8 个 IAP 商品（productId 见 Task 5 种子）。
2. 放置华为根证书 `config/iap/RootCaG2Ecdsa.cer` 与 ES256 私钥 `config/iap/priKey.p8`。
3. 设置环境变量 `IAP_KEY_ID`、`IAP_ISSUER_ID`、`IAP_APP_ID`。
4. 在 AppGallery 配置通知回调地址 `https://<域名>/api/iap/notifications`。

## Self-Review（计划自检）

- **Spec 覆盖**：
  - 模块划分 → Task 1-11 全部新建 `iap.*`。
  - 4 张表 → Task 5/6/8/11 各加一张。
  - 购买流程（预下单/上报/发货）→ Task 6/10。
  - 补单 → Task 10 `handleReport` 复用（purchaseData 同结构）。
  - 通知 5 类型 → Task 11 `dispatch`（DID_NEW_TRANSACTION/REVOKE/EXPIRE 处理；DID_CHANGE_RENEWAL_STATUS/RENEWAL_TIME_MODIFIED 记录）。
  - 退款回收 → Task 7 回收方法 + Task 10 `revokeByPurchaseOrder` + Task 11 REVOKE。
  - JWS 验签/JWT → Task 3/4。
  - 华为 REST → Task 9。
  - 鉴权（公开端点/IAP_PURCHASE/替换旧接口）→ Task 1/12。
  - 测试策略 → 每任务 TDD + Task 12 端到端。
  - **四种商品类型**：CONSUMABLE/NON_CONSUMABLE 走订单（Task 10 applyProductEffect）；NONRENEWABLE 走订单（Task 10 grantEntitlement→purchaseSubscription）；AUTORENEWABLE 走订阅（Task 10 resolveOrderPayload 取 jwsSubscriptionStatus，Task 11 subStatusQuery/subShippedConfirm）。✓
- **占位符扫描**：无 TBD/TODO；种子 8 行全部展开；所有步骤含完整代码与命令。
- **类型一致**：`grantFromOrderPayload`/`revokeByPurchaseOrder`/`handleReport`/`handleNotification` 签名跨任务一致；`IapOrderMapper.updateFulfillment`(Task 6)/`updateStatus`(Task 10)、`IapFulfillmentMapper.findByPurchaseOrderIdAndAction`(Task 8) 跨任务引用一致；payload record 字段与 Task 2 定义一致。

## Execution Handoff

计划已完成并保存到 `docs/superpowers/plans/2026-07-23-harmonyos-iap-integration.md`。两种执行方式：

1. **Subagent-Driven（推荐）** — 每个 Task 派发独立 subagent，任务间评审，迭代快。
2. **Inline Execution** — 在当前会话用 executing-plans 批量执行，带检查点评审。

请选择执行方式。

