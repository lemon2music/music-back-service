# HarmonyOS IAP Kit 服务端对接设计

- 日期：2026-07-23
- 模块：`iap`（新增领域模块，与 `user` / `membership` 并列）
- 依据技能：`harmonyos-iap-integration`（参考 `references/client-arkts.md`、`references/server-java.md`）

## 1. 背景与目标

现有 `membership` 模块已有商品系统（消耗型宝石加速卡、非消耗型终身顶级卡）与订阅系统（月/季/年卡），但购买接口（`/api/membership/products/purchase`、`/api/membership/subscribe`）是**调用即直接发放权益**，没有真实支付。

本设计接入 HarmonyOS IAP Kit，让所有付费购买走华为应用内支付：客户端拉起收银台 → 服务端验签发货 → 关键事件通知处理（含退款回收）。能力覆盖技能要求的三大标准流程：

- **数字商品购买标准流程**：环境检测 → 查询商品 → 服务器预下单 → 拉起收银台 → 上报 PurchaseData → 服务器验签发放权益 → 端侧确认发货
- **数字商品补单标准流程**：查询未确认购买 → 上报 PurchaseData → 服务器验签发放权益 → 端侧确认发货
- **关键事件通知标准流程**：华为服务器发通知 → 按类型处理业务 → 返回响应

## 2. 范围

### 2.1 商品类型（全部四种 IAP 商品类型）

| IAP 商品类型 | code | 内部对应 | 走流程 | 状态数据结构 |
|---|---|---|---|---|
| CONSUMABLE 消耗型 | 0 | 宝石等级加速卡 | 订单 | PurchaseOrderPayload |
| NON_CONSUMABLE 非消耗型 | 1 | 终身顶级卡 | 订单 | PurchaseOrderPayload |
| NONRENEWABLE 非自动续期订阅 | 3 | 月/季/年卡（一次性） | 订单 | PurchaseOrderPayload |
| AUTORENEWABLE 自动续期订阅 | 2 | 月/季/年卡（连续包月/季/年） | 订阅 | SubGroupStatusPayload |

### 2.2 已确认的关键决策

1. **订阅双支持**：月/季/年卡各配置「自动续期」+「非续期」两种 IAP 商品，端侧选择购买哪种。
2. **统一 IAP 商品映射表**：新建 `iap_product` 表统一管理所有 IAP 商品（消耗/非消耗/订阅）。
3. **替换旧购买接口**：新增 `/api/iap/*` 承载全部支付流程，删除旧的 `/api/membership/products/purchase` 与 `/api/membership/subscribe`（直接发放版）。
4. **退款回收策略**：积分商品扣回已发积分（不足扣到 0，积分不为负）；SVIP 终身卡降回 VIP；订阅 REVOKE/EXPIRE 置 `has_membership=false`。

### 2.3 不在本次范围

- 端侧 ArkTS 客户端代码（本仓库为 Java 后端；端侧对接要点见 §10）
- AppGallery Connect 商品配置、签名、根证书/密钥的获取（运营/开发者侧，§9 列出所需材料）

## 3. 模块划分

新增 `com.lemon.music.musicbackservice.iap`，遵循 user/membership 分层约定：

```
iap/
  domain/
    IapProductEntity / IapOrderEntity / IapFulfillmentEntity / IapNotificationLogEntity（@Data）
    IapProductType(枚举,带code) / InternalProductType / OrderStatus /
    FulfillmentAction / IapNotificationType / FulfillmentStatus
  dto/
    request/  CreateOrderRequest / ReportPurchaseRequest / IapNotificationRequest
    response/ IapProductResponse / PreOrderResponse / ReportPurchaseResponse
  mapper/
    IapProductMapper / IapOrderMapper / IapFulfillmentMapper / IapNotificationLogMapper
  service/
    IapOrderService           预下单
    IapFulfillmentService     验签发货 / 幂等 / 退款回收（核心）
    IapNotificationService    通知验签 + 分发
    IapHuaweiClient           调用华为 REST API（订单/订阅 查询、确认发货），含 JWT 鉴权
    IapProductService         商品映射查询
  support/
    JwsVerifier               JWS 验签并解码 payload（复用 server-java JWSChecker 逻辑）
    IapJwtGenerator           生成调用华为 REST 所需的 ES256 JWT
    IapProperties             @ConfigurationProperties("app.iap")
  controller/
    IapController             商品查询 / 预下单 / 上报（登录，需权限）
    IapNotificationController 关键事件通知回调（公开）
```

与 `membership` 的协作：`iap` 在验签通过后调用 `MembershipService.addPoints / upgradeMembershipType / purchaseSubscription` 发放权益；退款时反向回收（新增 `MembershipService` 的回收方法，见 §8）。

> `membership` 保持纯业务（积分/等级/订阅资格），`iap` 负责"支付凭证 → 业务权益"的翻译层，职责清晰、可独立测试。

## 4. 数据模型

新增 4 张表。SQL 保持 H2（MySQL 模式）兼容，与现有 `schema.sql` 风格一致（`CREATE TABLE IF NOT EXISTS`、`DATETIME`、`AUTO_INCREMENT`、`DECIMAL`、`BOOLEAN`）。

### 4.1 `iap_product` — 统一 IAP 商品映射

```sql
CREATE TABLE IF NOT EXISTS iap_product (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    internal_type VARCHAR(16) NOT NULL,           -- PRODUCT | SUBSCRIPTION
    internal_ref_id BIGINT NULL,                  -- 指向 product.id（internal_type=PRODUCT 时）
    subscription_type VARCHAR(16) NULL,           -- MONTHLY/QUARTERLY/YEARLY（internal_type=SUBSCRIPTION 时）
    huawei_product_id VARCHAR(128) NOT NULL,      -- 华为 IAP productId
    iap_product_type VARCHAR(24) NOT NULL,        -- CONSUMABLE/NON_CONSUMABLE/AUTORENEWABLE/NONRENEWABLE
    name VARCHAR(64) NOT NULL,
    price DECIMAL(10,2) NOT NULL,                 -- 内部记录价，展示用；真实以华为返回为准
    currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE/INACTIVE
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_iap_product_huawei_id UNIQUE (huawei_product_id)
);
```

### 4.2 `iap_order` — 业务订单（预下单落库）

```sql
CREATE TABLE IF NOT EXISTS iap_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,                -- 业务订单号，= developerPayload
    user_id BIGINT NOT NULL,
    iap_product_id BIGINT NOT NULL,
    huawei_product_id VARCHAR(128) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
    status VARCHAR(16) NOT NULL,                  -- PENDING/PAID/FULFILLED/REFUNDED/CLOSED
    huawei_purchase_order_id VARCHAR(64) NULL,    -- 上报后回填
    huawei_purchase_token VARCHAR(512) NULL,      -- 上报后回填
    created_at DATETIME NOT NULL,
    paid_at DATETIME NULL,
    fulfilled_at DATETIME NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_iap_order_no UNIQUE (order_no),
    INDEX idx_iap_order_user (user_id, created_at)
);
```

### 4.3 `iap_fulfillment` — 权益发放记录（防重复发货/重复回收）

```sql
CREATE TABLE IF NOT EXISTS iap_fulfillment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    huawei_purchase_order_id VARCHAR(64) NOT NULL,
    huawei_purchase_token VARCHAR(512) NULL,
    iap_order_id BIGINT NULL,
    user_id BIGINT NOT NULL,
    iap_product_id BIGINT NOT NULL,
    iap_product_type VARCHAR(24) NOT NULL,
    action VARCHAR(16) NOT NULL,                  -- GRANT | REVOKE
    points_granted INT NOT NULL DEFAULT 0,        -- 发放的积分数（退款回收依据）
    effect_snapshot TEXT NULL,                    -- 发放效果 JSON 快照（回收依据）
    created_at DATETIME NOT NULL,
    CONSTRAINT uk_iap_fulfillment_po_action UNIQUE (huawei_purchase_order_id, action)
);
```

> 幂等由 `(huawei_purchase_order_id, action)` 联合唯一约束保证：`GRANT` 唯一防重复发货，`REVOKE` 唯一防重复回收。并发上报触发唯一约束冲突时，捕获后按"已处理"对待。

### 4.4 `iap_notification_log` — 通知日志（排查 + 幂等）

```sql
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
    status VARCHAR(16) NOT NULL,                  -- SUCCESS/FAILED/IGNORED
    error_message VARCHAR(512) NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT uk_iap_notification_req UNIQUE (notification_request_id)
);
```

> 同一 `notification_request_id` 重复投递时，插入冲突即视为已处理，幂等返回成功（HTTP 200）。`FAILED` 记录保留供人工排查（不做自动重试，简化处理；通知逻辑本身是确定性的）。

### 4.5 `@MapperScan` 更新

`MusicBackServiceApplication` 与 `config/MyBatisConfig` 两处的 `@MapperScan` 都需加入 `com.lemon.music.musicbackservice.iap.mapper`（CLAUDE.md 强调两处同步）。

### 4.6 种子数据（`data.sql`，幂等）

放在现有 `product` 种子之后，价格仅示例（真实以 AppGallery 配置 + 华为返回为准）：

```sql
-- 消耗型：宝石等级加速卡（关联 product.id）
INSERT INTO iap_product(internal_type, internal_ref_id, huawei_product_id, iap_product_type, name, price, currency, status, created_at, updated_at)
SELECT 'PRODUCT', p.id, 'iap_gem_card_001', 'CONSUMABLE', '宝石等级加速卡', 9.90, 'CNY', 'ACTIVE', NOW(), NOW()
FROM product p
WHERE p.product_name = '宝石等级加速卡'
  AND NOT EXISTS (SELECT 1 FROM iap_product ip WHERE ip.huawei_product_id = 'iap_gem_card_001');

-- 非消耗型：终身顶级卡
INSERT INTO iap_product(internal_type, internal_ref_id, huawei_product_id, iap_product_type, name, price, currency, status, created_at, updated_at)
SELECT 'PRODUCT', p.id, 'iap_lifetime_svip_001', 'NON_CONSUMABLE', '终身顶级卡', 1999.00, 'CNY', 'ACTIVE', NOW(), NOW()
FROM product p
WHERE p.product_name = '终身顶级卡'
  AND NOT EXISTS (SELECT 1 FROM iap_product ip WHERE ip.huawei_product_id = 'iap_lifetime_svip_001');

-- 订阅：月/季/年卡 × (自动续期 + 非续期)，共 6 行
INSERT INTO iap_product(internal_type, subscription_type, huawei_product_id, iap_product_type, name, price, currency, status, created_at, updated_at)
SELECT 'SUBSCRIPTION', 'MONTHLY', 'iap_sub_monthly_auto', 'AUTORENEWABLE', '月卡(连续包月)', 18.00, 'CNY', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM iap_product ip WHERE ip.huawei_product_id = 'iap_sub_monthly_auto');
-- …同理 QUARTERLY/YEARLY × AUTORENEWABLE/NONRENEWABLE，huawei_product_id 分别为
-- iap_sub_quarterly_auto / iap_sub_yearly_auto / iap_sub_monthly_once / iap_sub_quarterly_once / iap_sub_yearly_once
```

## 5. 枚举定义

```java
public enum IapProductType {
    CONSUMABLE(0), NON_CONSUMABLE(1), AUTORENEWABLE(2), NONRENEWABLE(3);
    private final int code;          // 对应华为 type 数字
    // fromCode(int)、getCode()
}
public enum InternalProductType { PRODUCT, SUBSCRIPTION }
public enum OrderStatus { PENDING, PAID, FULFILLED, REFUNDED, CLOSED }
public enum FulfillmentAction { GRANT, REVOKE }
public enum IapNotificationType {
    DID_NEW_TRANSACTION, DID_CHANGE_RENEWAL_STATUS, REVOKE, RENEWAL_TIME_MODIFIED, EXPIRE
}
```

> `iap_product_type` / `subscription_type` 等列按**名称**存储（与现有 `membership_type`、`UserStatus` 一致，依赖 MyBatis 默认 `EnumTypeHandler`）。`IapProductType` 额外携带华为数字 `code`，用于 DTO 输出与解析 `PurchaseData.type`。

## 6. 配置项（`app.iap`）

```yaml
app:
  iap:
    base-url: https://iap.cloud.huawei.com
    root-ca-cert-path: ${user.dir}/config/iap/RootCaG2Ecdsa.cer   # JWS 验签根证书
    jwt:
      private-key-path: ${user.dir}/config/iap/priKey.p8           # 调用 REST 的 ES256 私钥
      key-id: ${IAP_KEY_ID:}        # 以下三项从 AppGallery Connect 获取，环境变量注入，不入库
      issuer-id: ${IAP_ISSUER_ID:}
      app-id: ${IAP_APP_ID:}
```

`pom.xml` 新增依赖：`com.auth0:java-jwt:4.4.0`、`commons-codec:commons-codec:1.18`（`DigestUtils.sha256Hex`）。

## 7. 接口契约

所有响应统一走 `common/ApiResponse<T>` 信封。DTO 一律用 **record + Jakarta Validation**，禁用 `Map`/`JsonNode` 作 DTO（技能强制：强类型防字段拼写错误；`JsonNode` 仅在解析华为返回的 JWS payload 内部使用）。

### 7.1 `GET /api/iap/products`（登录，无需特殊权限）

返回上架的 IAP 商品映射列表，供端侧 `queryProducts` 后展示。

```java
record IapProductResponse(
    Long id, String name, String huaweiProductId, IapProductType iapProductType,
    BigDecimal price, String currency, SubscriptionType subscriptionType, String description
) {}
```

### 7.2 `POST /api/iap/orders`（登录，`@RequirePermission("IAP_PURCHASE")`）

预下单：创建 `iap_order(PENDING)`，返回订单号作为端侧 `developerPayload`。

```java
record CreateOrderRequest(@NotNull Long iapProductId) {}
record PreOrderResponse(
    String orderNo, String huaweiProductId, IapProductType iapProductType,
    BigDecimal amount, String currency
) {}
```

### 7.3 `POST /api/iap/orders/report`（登录，`@RequirePermission("IAP_PURCHASE")`）

上报 `purchaseData`：端侧 `createPurchase` / `queryPurchases` 拿到的原始 JSON 字符串。补单流程复用本接口。

```java
record ReportPurchaseRequest(
    @NotNull IapProductType iapProductType,
    @NotBlank String purchaseData            // createPurchase/queryPurchases 返回的原始 JSON
) {}
record ReportPurchaseResponse(boolean fulfilled, Long orderId, String message) {}
```

### 7.4 `POST /api/iap/notifications`（**公开**，华为服务器调用）

```java
record IapNotificationRequest(@NotBlank String jwsNotification) {}
// 响应：ApiResponse.ok（华为只看 HTTP 200；处理失败也返回 200 并记录 FAILED，避免华为无限重试）
```

### 7.5 鉴权与权限调整

- `AuthInterceptor` 公开端点加入 `/api/iap/notifications`（路径前缀白名单）。
- `data.sql` 新增权限 `IAP_PURCHASE`（"IAP 商品购买"），授予 `USER`、`ADMIN` 角色。
- 删除旧的 `MEMBERSHIP_PURCHASE` / `PRODUCT_PURCHASE` / `PRODUCT_USE` 权限及其 `@RequirePermission` 用法（随旧接口删除）。

## 8. 核心流程

### 8.1 发放权益（`IapFulfillmentService.grant`）

按 `iap_product_type` + `internal_type` 分发到 `MembershipService`：

- CONSUMABLE + `POINTS_BONUS` → `MembershipService.addPoints(userId, points, PRODUCT_REDEEM, "IAP购买:宝石加速卡")`
- NON_CONSUMABLE + `MEMBERSHIP_UPGRADE` → `MembershipService.upgradeMembershipType(userId, SVIP)`
- NONRENEWABLE / AUTORENEWABLE 订阅 → `MembershipService.purchaseSubscription(userId, subscriptionType)`（按订阅续期逻辑累加到期时间）

发放时写 `iap_fulfillment(action=GRANT, points_granted=发放积分, effect_snapshot=效果JSON)`，回填 `iap_order(purchaseOrderId/purchaseToken, status=FULFILLED, fulfilled_at)`。

### 8.2 退款回收（`IapFulfillmentService.revoke`，新增）

`MembershipService` 新增回收方法（对称于发放）：

- 积分回收：`MembershipService.deductPoints(userId, points, IAP_REVOKE, "IAP退款回收")` —— 扣到 0 为止（积分不为负，复用现有约束）。`points` 取自原 GRANT 记录的 `points_granted`。
- SVIP 降级：`MembershipService.downgradeMembershipType(userId, VIP)`。
- 订阅回收：`MembershipService.revokeSubscription(userId)` —— 置 `has_membership=false`（与现有订阅到期处理一致）。

回收时写 `iap_fulfillment(action=REVOKE, effect_snapshot=回收动作)`，更新 `iap_order(status=REFUNDED)`。

### 8.3 上报处理（购买 + 补单，`IapFulfillmentService.handleReport`）

```
1. 解析 purchaseData(JSON) → 取 type 字段
2. 按 iapProductType 取凭证：
     订单类(CONSUMABLE/NON_CONSUMABLE/NONRENEWABLE) → jwsPurchaseOrder
     订阅类(AUTORENEWABLE) → jwsSubscriptionStatus → 取 lastSubscriptionStatus.lastPurchaseOrder
3. JwsVerifier.checkAndDecode(jws) → PurchaseOrderPayload
4. 幂等：若 fulfillment(purchaseOrderId, GRANT) 已存在 → 直接返回 fulfilled=false("已发放")
5. 退款校验：purchaseOrderRevocationReasonCode 非空 → 不发货（记录后返回）
6. 用 developerPayload(=orderNo) 反查 iap_order → 校验 userId、huaweiProductId 一致
7. 发放权益(§8.1) + 写 fulfillment + 回填订单
8. 返回 fulfilled=true
   （端侧随后调用 finishPurchase 确认发货 —— 由端完成）
```

### 8.4 关键事件通知（`IapNotificationService.handleNotification`）

```
1. JwsVerifier.checkAndDecode(jwsNotification) → NotificationPayload
2. 幂等：notification_request_id 唯一约束；冲突即视为已处理返回
3. 按 notificationType 分发：
   DID_NEW_TRANSACTION:
     区分商品类型调华为 REST 查最新状态(orderStatusQuery/subStatusQuery) → 验签解码
     → 退款/已发放跳过 → developerPayload 找订单 → 发放权益(§8.1)
     → 服务端调 shipped/confirm 确认发货（无端侧参与，由服务端确认）
   REVOKE(退款):
     查最新状态 → 验签解码 → 按 fulfillment(GRANT) 快照回收权益(§8.2)
   EXPIRE(订阅过期):
     MembershipService.revokeSubscription(userId) → has_membership=false
   DID_CHANGE_RENEWAL_STATUS:
     仅记录日志（自动续费开关），不动权益
   RENEWAL_TIME_MODIFIED:
     更新本地订阅到期时间记录（日志）
4. 写 notification_log(status=SUCCESS/FAILED)，返回 200
```

### 8.5 调用华为 REST（`IapHuaweiClient`）

复用 `references/server-java.md` 的 `IAPServer.httpPost` + `JWTGenerator` + `OrderService`/`SubscriptionService` 结构，适配为 Spring Bean（`RestClient`/`HttpURLConnection` 二选一，默认 `RestClient`）：

| 操作 | 路径 |
|---|---|
| 订单状态查询 | `POST /order/harmony/v1/application/order/status/query` |
| 订单确认发货 | `POST /order/harmony/v1/application/purchase/shipped/confirm` |
| 订阅状态查询 | `POST /subscription/harmony/v1/application/subscription/status/query` |
| 订阅确认发货 | `POST /subscription/harmony/v1/application/purchase/shipped/confirm` |

每次请求用 `IapJwtGenerator` 生成 `Authorization: Bearer <jwt>`（ES256，header 含 kid，payload 含 iss/aud/iat/exp/aid/digest=SHA256(body)）。

### 8.6 JWS 验签（`support/JwsVerifier`）

移植 `references/server-java.md` 的 `JWSChecker`：
- 校验 `alg=ES256`、`x5c` 证书链长度=3
- 用 `app.iap.root-ca-cert-path` 根证书做 PKIX 校验（`setRevocationEnabled(false)`）
- 校验叶子证书含 OID `1.3.6.1.4.1.2011.2.415.1.1`
- 用证书链解析出的公钥 `Algorithm.ECDSA256` 验签
- 返回 base64url 解码后的 payload JSON 字符串

提供两个便捷方法：`checkAndDecode(jws)` 返回原始 payload String；调用方用强类型 record 反序列化（`PurchaseOrderPayload` / `SubGroupStatusPayload` / `NotificationPayload`，字段严格对齐 `references/client-arkts.md`）。

## 9. 所需外部材料（需开发者/运营提供）

1. AppGallery Connect：开启应用内购买服务，配置 `client_id`，上架 8 个 IAP 商品（productId 见 §4.6）。
2. JWS 验签：华为根证书 `RootCaG2Ecdsa.cer`，放 `config/iap/`。
3. REST 鉴权：ES256 私钥 `priKey.p8`、Key ID、Issuer ID、App ID，通过环境变量 `IAP_KEY_ID`/`IAP_ISSUER_ID`/`IAP_APP_ID` 注入。
4. 通知回调地址：`https://<域名>/api/iap/notifications` 在 AppGallery 配置。

## 10. 端侧对接要点（非本仓库交付，供参考）

- 预下单 `POST /api/iap/orders` 拿 `orderNo` → 作为 `developerPayload` 传给 `iap.createPurchase`。
- `createPurchase` / `queryPurchases` 成功后，把原始 `purchaseData` 字符串上报 `POST /api/iap/orders/report`，服务端验签发货。
- 上报成功后端侧 `iap.finishPurchase` 确认发货（订单类）。
- 商品类型用 `iap_product_type` 决定端侧 `ProductType` 与 `queryPurchases` 的 `queryType`（详见 `references/client-arkts.md`）。

## 11. 错误处理与幂等

- 业务规则失败抛 `BusinessException`（→ HTTP 400），与现有一致。
- JWS 验签失败、华为 REST 失败：上报接口抛 `BusinessException`（端侧可重试上报）；通知接口记录 `FAILED` 并返回 200。
- 幂等三重保障：`iap_fulfillment(purchaseOrderId, action)` 唯一约束（发货/回收）、`iap_notification_log(request_id)` 唯一约束（通知）、上报前先查 fulfillment。
- 并发上报同一 `purchaseOrderId`：依赖唯一约束，捕获冲突后按"已处理"返回，避免重复发放。

## 12. 测试策略

- **单元测试**
  - `JwsVerifier`：用自签 ES256 测试证书验证验签/解码/证书链校验。
  - `IapJwtGenerator`：测试 JWT 生成与 digest 计算。
  - `IapFulfillmentService`：mock `MembershipService` 与 `IapHuaweiClient`，覆盖四种商品发放、退款回收、幂等（重复上报不重复发放）、退款订单不发货。
  - `IapNotificationService`：五种通知类型分发、`request_id` 幂等。
- **集成测试**
  - 预下单 → 上报 → 发货 端到端（mock 华为 REST；H2 + mock `StringRedisTemplate`，沿用 `MusicBackServiceApplicationTests` 既有套路）。
  - 通知 REVOKE → 权益回收 端到端。
- SQL 保持 H2 兼容（无 MySQL 专有语法），与现有测试配置一致。

## 13. 风险

- **外部材料未就绪**：根证书/密钥/商品未配置时，验签与 REST 调用会失败。开发期需准备测试材料或用 mock。
- **密钥安全**：私钥、Key/Issuer/App ID 通过环境变量注入，不入库；`config/iap/` 加入 `.gitignore`。
- **价格一致性**：内部 `iap_product.price` 仅展示用，真实扣费以华为为准，预下单不校验金额。
