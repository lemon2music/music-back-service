# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 构建、运行、测试

基于 Maven wrapper 的项目（Windows 环境；git-bash shell 下可直接运行 `./mvnw`）：

- 构建：`./mvnw clean package`（加 `-DskipTests` 可跳过测试阶段）
- 启动应用：`./mvnw spring-boot:run`（默认端口 8080——未配置 `server.port`）
- 运行全部测试：`./mvnw test`
- 运行单个测试类：`./mvnw test -Dtest=MusicBackServiceApplicationTests`
- 运行单个测试方法：`./mvnw test -Dtest=MusicBackServiceApplicationTests#contextLoads`

需要 Java 21。项目未集成 checkstyle/spotless/format 之类的插件，因此不强制代码格式。

## 技术栈

Spring Boot **4.1.0**（web starter 为 `spring-boot-starter-webmvc`，而非 `…-web`），MyBatis 3.0.4 采用**注解式** mapper（无 XML mapper 文件），spring-data-redis，spring-security-**crypto**（仅用 BCrypt——见下方鉴权说明），MySQL（运行时）+ H2（测试），Lombok，Jakarta Validation。所有代码位于包 `com.lemon.music.musicbackservice` 下。

## 架构

### 鉴权是自定义实现——并非 Spring Security

引入 `spring-security-crypto` 依赖**仅仅**是为了用 `BCryptPasswordEncoder`。项目没有 Spring Security 的 filter chain、没有 `SecurityContext`、没有 `@PreAuthorize`。鉴权完全通过一个普通的 MVC 拦截器实现：

- `auth/AuthInterceptor` 在 `config/WebConfig` 中注册到 `/**`。每个请求进来时，它会取出 `Authorization: Bearer <token>` 头，通过 `AuthService` 在 Redis 中查 session，再用 `@RequirePermission` 注解校验 session 的权限集合，最后把 session 放进 `auth/AuthContext`（一个 `ThreadLocal`，在 `afterCompletion` 中清除）。
- **公开端点**（无需 token）通过路径前缀硬编码在 `AuthInterceptor.preHandle` 中：`/api/auth/login` 与 `/api/users/register`。任何其他需要公开的路由都必须在那里添加。
- `auth/RequirePermission` 是一个注解，可用于**方法**或**类**。拦截器会拿 session 的 `Set<String> permissions` 做精确字符串匹配。
- 在 controller/service 中获取当前用户，调用 `AuthContext.get()` → `UserSession`。

### Token 模型：Redis 中的不透明 UUID，不是 JWT

`AuthService.login` 生成一个随机 UUID token，并把 JSON 序列化后的 `UserSession` 存入 Redis，key 为 `auth:token:{token}`（TTL = `app.auth.token-expire-hours`，默认 168h = 7 天）。发给某用户的所有 token 都记录在 Redis 集合 `auth:user:tokens:{userId}` 中（供 `logoutAllByUserId` 使用）。累计在线时长记录在 `auth:online:total:{userId}` 下，注销时按本 session 的经过秒数累加。

### RBAC 数据模型

数据表：`app_user`、`app_role`、`app_permission`、`user_role`、`role_permission`（见 `src/main/resources/schema.sql`）。登录时由 `PermissionMapper.findPermissionCodesByUserId` 把权限展平进 session（联表路径：`user_role` → `role_permission` → `app_permission`）。种子角色/权限定义在 `src/main/resources/data.sql` 中，使用 `NOT EXISTS` 守卫以保证幂等。当前权限码：`USER_SELF`、`USER_MANAGE_MEMBERSHIP`、`USER_ASSIGN_ROLE`；角色 `USER`、`ADMIN`。

### 包结构 / 模块约定

`user` 包是参考模块。新增领域时照此结构组织：

```
user/
  domain/   实体（Lombok @Data，可变）+ 枚举
  dto/      请求/响应 record，带 Jakarta validation 注解
  mapper/   MyBatis @Mapper 接口（SQL 以 @Select/@Insert/@Update 字符串形式内联）
  service/  @Service + @Transactional；违反业务规则时抛 BusinessException
  controller/ @RestController，统一挂在 /api/... 下
```

- **DTO、值对象、枚举、配置 record 一律用 `record`。** DB 实体（`@Data` 类）是例外。所有依赖均通过 Lombok `@RequiredArgsConstructor` 构造器注入。
- 每个 controller 方法都返回 `common/ApiResponse<T>`——即 `{success, message, data}` 信封。使用 `ApiResponse.ok(...)` / `ApiResponse.fail(...)` 工厂方法。
- **坑——mapper 扫描需在两处同步声明。** `@MapperScan` 在 `MusicBackServiceApplication` 与 `config/MyBatisConfig` **两处**都声明了（冗余），当前扫描 `user.mapper` 与 `membership.mapper` 两个包。新增领域的 mapper 必须在两处都加上对应包。（单独标注了 `@Mapper` 的接口也会被扫描，但显式的 `@MapperScan` 才是可靠路径。）
- MyBatis 开启了 `map-underscore-to-camel-case: true`，所以 DB 的 `snake_case` 列会自动映射到 camelCase 字段。

### 错误处理

`common/GlobalExceptionHandler`（`@RestControllerAdvice`）的映射规则：`BusinessException` → HTTP 400；校验失败（`MethodArgumentNotValidException`/`BindException`）→ 400 并返回第一个字段错误的 message；其他任何 `Exception` → 500。在 service 中遇到预期的业务规则失败就抛 `common/BusinessException(message)`；鉴权失败不要靠抛异常——401 的 JSON 是由拦截器自己写的。

### 持久化初始化每次启动都执行

`spring.sql.init.mode: always` 使得 `schema.sql`（`CREATE TABLE IF NOT EXISTS`）和 `data.sql` 在**每次**启动时都执行，dev 和 test 皆是如此。请保持它们幂等。

### 会员系统（`membership` 模块）

参照 `user` 模块的分层结构，是一个独立领域模块（domain/dto/mapper/service/controller）。已取代旧的 `MembershipLevel`（NORMAL/GOLD/DIAMOND）简单等级。

- **等级与积分**：`VipLevel` VIP1-VIP5（按积分区间 1-1000 / 1001-3000 / 3001-10000 / 10001-49999 / 50000+），`MembershipType` 分 `VIP`/`SVIP`（SVIP 每日积分翻倍）。等级随积分自动升降（`MembershipService.calculateLevelByPoints`）。
- **定时任务**：`MembershipScheduler` 每天凌晨 1 点（`@EnableScheduling` 已在主类开启）跑 `MembershipService.processDailyPointsChange()`——有会员资格按等级增长积分，失去资格则反向扣减（积分不为负）。每用户独立事务（用 `TransactionTemplate`），单用户失败不影响他人。
- **会员资格**：购买订阅（`SubscriptionType`：月/季/年卡）获得，到期后 `has_membership` 置 false 并开始反向扣减。
- **商品系统**：`product.effect_config`（JSON 字符串）驱动效果——`POINTS_BONUS`（加积分）/ `MEMBERSHIP_UPGRADE`（升级 SVIP）。消耗性商品（宝石等级加速卡）购买后入库待用；非消耗性商品（终身顶级卡）购买即生效且只能买一次，用 `UserMapper.lockById`（`SELECT ... FOR UPDATE`）行锁串行化同一用户的并发购买。
- **鉴权**：查询接口（`/api/membership/info`、`/points-history`、`/products`）需登录但无需特定权限；写接口需 `MEMBERSHIP_PURCHASE`/`PRODUCT_PURCHASE`/`PRODUCT_USE` 权限。
- **枚举按名称存储**：MyBatis 默认 `EnumTypeHandler`，`membership_type`/`vip_level` 等列存 `'VIP'`、`'VIP1'` 字符串，与 `UserStatus` 一致。
- **遗留列**：`app_user.membership_level`（旧系统，`NOT NULL`）仍在表里，`UserMapper.insert` 写固定值 `'NORMAL'`，代码已不引用；可日后手动 `ALTER TABLE app_user DROP COLUMN membership_level` 清理。

### IAP订单系统（`iap` 模块）

HarmonyOS应用内购买系统，参照 `user` 模块的分层结构。支持消费型商品和订阅型商品的购买、验签、发货和取消。

- **核心功能**：
  - 预下单（`POST /api/iap/orders`）：生成订单号供客户端调用华为IAP收银台
  - 购买上报（`POST /api/iap/orders/report`）：客户端支付成功后上报purchaseData，服务端验签后发放权益
  - 订单取消（`PUT /api/iap/orders/{orderNo}/cancel`）：用户取消支付时更新订单状态为CLOSED
  - 订单查询（`GET /api/iap/products`）：查询上架的IAP商品列表
  - 订单通知（`POST /api/iap/notifications`）：接收华为IAP服务端的订阅状态变更通知

- **订单状态流转**：`PENDING`（预下单）→ `PAID`（支付成功）→ `FULFILLED`（已发货）/ `REFUNDED`（已退款）/ `CLOSED`（已取消）

- **取消订单功能**：
  - 端点：`PUT /api/iap/orders/{orderNo}/cancel`
  - 鉴权：需登录（`USER_SELF`权限）
  - 权限验证：只有订单所有者可以取消，且只能取消PENDING状态的订单
  - 请求体（可选）：`{ "cancelReason": "用户主动取消" }`
  - 响应：`{ "success": true, "message": "订单已取消", "data": true }`
  - 数据库变更：更新订单状态为CLOSED，记录cancelled_at和cancel_reason

- **数据库设计**：
  - `iap_order`：订单主表（order_no, user_id, iap_product_id, status, cancelled_at, cancel_reason）
  - `iap_product`：商品配置表（huawei_product_id, internal_product_type, price）
  - `iap_fulfillment`：发货记录表（order_no, purchase_token, notification_id）
  - `iap_notification_log`：通知日志表（notification_type, jws_notification）

- **安全性设计**：
  - JWT验签：使用华为公钥验证purchaseData签名
  - 幂等性保证：订单状态条件更新，防止重复发货
  - 权限验证：订单所有权校验，状态机严格控制
  - 订阅通知：华为服务端推送，需验证notificationSignature

- **枚举按名称存储**：`OrderStatus`、`IapProductType`、`IapNotificationType` 等枚举存储为字符串（'PENDING', 'PAID', 'FULFILLED'）

- **华为IAP集成**：
  - 预下单时order_no作为developerPayload传递给华为收银台
  - 购买上报时解析purchaseData获取订单信息进行验签
  - 支持消费型商品（CONSUMABLE）和订阅型商品（SUBSCRIPTION）
  - 订阅类型支持自动续订（AUTO_RENEWABLE）

## 配置 profile

- **主配置（`application.yaml`）** 指向远程 dev 环境的 MySQL 与 Redis（`47.119.121.254`），且凭据已提交进仓库（`admin`/`Test@123456`）。请注意这些是已入库的真实 dev 环境密钥。
- **测试配置（`src/test/resources/application.yaml`）** 把 MySQL 换成内存 H2 的 MySQL 模式（`MODE=MySQL`），排除了 Redis 自动配置，并且上下文测试里把 `StringRedisTemplate` 用 `@MockitoBean` 模拟掉了。因此 mapper 中的 SQL 必须保持 H2 兼容（不要用 MySQL 专有语法），否则测试会失败。
