# 会员系统设计文档

**创建时间：** 2025-01-23  
**项目：** music-back-service  
**作者：** Claude Code  
**状态：** 设计阶段

## 1. 概述

### 1.1 背景
现有的会员系统过于简单（仅包含 NORMAL、GOLD、DIAMOND 三个等级），无法支持复杂的会员积分和等级体系。需要重新设计一个基于积分的动态会员系统，支持多种等级、会员类型（VIP/SVIP）、订阅机制和商品兑换功能。

### 1.2 目标
- 建立基于积分的动态会员等级系统（VIP1-VIP5 + SVIP）
- 实现每日积分自动增长/扣减机制
- 支持会员订阅购买和到期管理
- 提供商品系统（消耗性和非消耗性商品）
- 记录完整的积分变化历史

### 1.3 核心业务规则
- **等级与积分关系：**
  - VIP1: 1-1000积分
  - VIP2: 1001-3000积分  
  - VIP3: 3001-10000积分
  - VIP4: 10001-50000积分
  - VIP5: 50000+积分

- **会员类型：**
  - VIP（普通会员）：按等级规则增长积分
  - SVIP（超级会员）：积分增长翻倍

- **每日积分规则：**
  - VIP1: +1积分/天，失去资格后-1积分/天
  - VIP2: +2积分/天，失去资格后-2积分/天
  - VIP3: +3积分/天，失去资格后-3积分/天
  - VIP4: +4积分/天，失去资格后-4积分/天
  - VIP5: +5积分/天，失去资格后-5积分/天
  - SVIP用户：积分翻倍（VIP1→+2, VIP2→+4, VIP3→+6, VIP4→+8, VIP5→+10）

- **等级变化：**
  - 积分上升达到阈值时自动升级
  - 积分下降到阈值以下时自动降级
  - SVIP会员也有VIP1-VIP5等级，积分增长翻倍

## 2. 数据模型设计

### 2.1 数据库表结构

#### 2.1.1 membership（会员信息表）

```sql
CREATE TABLE membership (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    current_points INT NOT NULL DEFAULT 0,
    membership_type VARCHAR(16) NOT NULL,  -- VIP, SVIP
    vip_level VARCHAR(8) NOT NULL,  -- VIP1, VIP2, VIP3, VIP4, VIP5
    has_membership BOOLEAN NOT NULL DEFAULT FALSE,  -- 是否有会员资格
    subscription_expire_at DATETIME NULL,  -- 订阅到期时间
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_membership_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);
```

**字段说明：**
- `user_id`: 用户ID，唯一标识
- `current_points`: 当前积分
- `membership_type`: 会员类型（VIP/SVIP）
- `vip_level`: VIP等级（VIP1-VIP5），SVIP用户也有此字段
- `has_membership`: 是否有有效的会员资格（订阅是否有效）
- `subscription_expire_at`: 订阅到期时间，NULL表示无订阅

#### 2.1.2 membership_points_history（积分历史表）

```sql
CREATE TABLE membership_points_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    points_change INT NOT NULL,  -- 变化量（正负）
    points_before INT NOT NULL,  -- 变化前积分
    points_after INT NOT NULL,   -- 变化后积分
    change_reason VARCHAR(32) NOT NULL,  -- DAILY_GAIN, DAILY_LOSS, PRODUCT_REDEEM, SUBSCRIPTION_PURCHASE, SUBSCRIPTION_EXPIRE
    description VARCHAR(255) NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_user_created (user_id, created_at),
    CONSTRAINT fk_points_history_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);
```

**字段说明：**
- `points_change`: 积分变化量，正数表示增加，负数表示减少
- `change_reason`: 变化原因枚举值
- `description`: 变化描述（可选）

#### 2.1.3 membership_subscription（订阅记录表）

```sql
CREATE TABLE membership_subscription (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    subscription_type VARCHAR(16) NOT NULL,  -- MONTHLY, QUARTERLY, YEARLY
    start_time DATETIME NOT NULL,
    expire_time DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_user_time (user_id, start_time),
    CONSTRAINT fk_subscription_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);
```

**字段说明：**
- `subscription_type`: 订阅类型（月卡/季卡/年卡）
- `start_time`: 订阅开始时间
- `expire_time`: 订阅到期时间

#### 2.1.4 product（商品表）

```sql
CREATE TABLE product (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_name VARCHAR(64) NOT NULL,
    product_type VARCHAR(16) NOT NULL,  -- CONSUMABLE, NON_CONSUMABLE
    price DECIMAL(10,2) NOT NULL,
    effect_config JSON NOT NULL,  -- 商品效果配置
    description VARCHAR(255) NULL,
    status VARCHAR(16) NOT NULL,  -- ACTIVE, INACTIVE
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);
```

**字段说明：**
- `product_type`: 商品类型（消耗性/非消耗性）
- `effect_config`: JSON格式配置商品效果
- `status`: 商品状态

#### 2.1.5 user_product（用户购买记录表）

```sql
CREATE TABLE user_product (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    purchase_time DATETIME NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,  -- 是否已使用（消耗性商品）
    used_time DATETIME NULL,
    INDEX idx_user_time (user_id, purchase_time),
    CONSTRAINT fk_user_product_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_user_product_product FOREIGN KEY (product_id) REFERENCES product(id)
);
```

**字段说明：**
- `used`: 消耗性商品是否已使用
- `used_time`: 使用时间

### 2.2 枚举定义

#### 2.2.1 MembershipType（会员类型）
```java
public enum MembershipType {
    VIP,    // 普通会员
    SVIP    // 超级会员（积分增长翻倍）
}
```

#### 2.2.2 VipLevel（VIP等级）
```java
public enum VipLevel {
    VIP1,   // 1-1000积分
    VIP2,   // 1001-3000积分
    VIP3,   // 3001-10000积分
    VIP4,   // 10001-50000积分
    VIP5    // 50000+积分
}
```

#### 2.2.3 PointsChangeReason（积分变化原因）
```java
public enum PointsChangeReason {
    DAILY_GAIN,              // 每日增长
    DAILY_LOSS,              // 每日扣减
    PRODUCT_REDEEM,          // 商品兑换
    SUBSCRIPTION_PURCHASE,   // 订阅购买
    SUBSCRIPTION_EXPIRE      // 订阅到期
}
```

#### 2.2.4 ProductType（商品类型）
```java
public enum ProductType {
    CONSUMABLE,      // 消耗性商品（可重复购买）
    NON_CONSUMABLE   // 非消耗性商品（只能购买一次）
}
```

#### 2.2.5 SubscriptionType（订阅类型）
```java
public enum SubscriptionType {
    MONTHLY,    // 月卡（30天）
    QUARTERLY,  // 季卡（90天）
    YEARLY      // 年卡（365天）
}
```

### 2.3 积分增长规则表

| 会员类型 | 等级 | 每日积分增长 | 失去资格后每日扣减 |
|---------|------|------------|----------------|
| VIP     | VIP1 | +1         | -1            |
| VIP     | VIP2 | +2         | -2            |
| VIP     | VIP3 | +3         | -3            |
| VIP     | VIP4 | +4         | -4            |
| VIP     | VIP5 | +5         | -5            |
| SVIP    | VIP1 | +2         | -2            |
| SVIP    | VIP2 | +4         | -4            |
| SVIP    | VIP3 | +6         | -6            |
| SVIP    | VIP4 | +8         | -8            |
| SVIP    | VIP5 | +10        | -10           |

## 3. 架构设计

### 3.1 技术架构

采用传统服务层架构，符合现有项目风格：

```
Controller Layer (API接口)
    ↓
Service Layer (业务逻辑)
    ↓
Mapper Layer (数据访问)
    ↓
Database (MySQL)
```

### 3.2 包结构

```
com.lemon.music.musicbackservice.membership/
  domain/                    实体和枚举
    MembershipEntity.java
    MembershipPointsHistoryEntity.java
    MembershipSubscriptionEntity.java
    ProductEntity.java
    UserProductEntity.java
    MembershipType.java
    VipLevel.java
    PointsChangeReason.java
    ProductType.java
    SubscriptionType.java
  dto/                       请求和响应DTO
    request/
      PurchaseSubscriptionRequest.java
      PurchaseProductRequest.java
    response/
      MembershipInfoResponse.java
      PointsHistoryResponse.java
      ProductInfoResponse.java
  mapper/                    MyBatis Mapper
    MembershipMapper.java
    MembershipPointsHistoryMapper.java
    MembershipSubscriptionMapper.java
    ProductMapper.java
    UserProductMapper.java
  service/                   业务逻辑
    MembershipService.java
    ProductService.java
    MembershipScheduler.java (定时任务)
  controller/                API接口
    MembershipController.java
```

### 3.3 MyBatis Mapper扫描配置

需要在以下两处添加membership包的扫描：

```java
// MusicBackServiceApplication.java
@MapperScan({"com.lemon.music.musicbackservice.user.mapper", 
            "com.lemon.music.musicbackservice.membership.mapper"})

// MyBatisConfig.java  
@MapperScan({"com.lemon.music.musicbackservice.user.mapper",
            "com.lemon.music.musicbackservice.membership.mapper"})
```

## 4. API接口设计

### 4.1 Controller接口

#### 4.1.1 MembershipController

```java
@RestController
@RequestMapping("/api/membership")
@RequiredArgsConstructor
public class MembershipController {
    
    private final MembershipService membershipService;
    private final ProductService productService;
    
    // 查询会员基本信息（积分和等级）
    @GetMapping("/info")
    public ApiResponse<MembershipInfoResponse> getMembershipInfo()
    
    // 查询积分历史
    @GetMapping("/points-history")
    public ApiResponse<List<PointsHistoryResponse>> getPointsHistory(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size)
    
    // 购买会员订阅
    @PostMapping("/subscribe")
    @RequirePermission("MEMBERSHIP_PURCHASE")
    public ApiResponse<Void> purchaseSubscription(
        @RequestBody @Valid PurchaseSubscriptionRequest request)
    
    // 获取可用商品列表
    @GetMapping("/products")
    public ApiResponse<List<ProductInfoResponse>> getAvailableProducts()
    
    // 购买/兑换商品
    @PostMapping("/products/purchase")
    @RequirePermission("PRODUCT_PURCHASE")
    public ApiResponse<Void> purchaseProduct(
        @RequestBody @Valid PurchaseProductRequest request)
    
    // 使用消耗性商品
    @PostMapping("/products/{userProductId}/use")
    @RequirePermission("PRODUCT_USE")
    public ApiResponse<Void> useProduct(@PathVariable Long userProductId)
}
```

### 4.2 DTO设计

#### 4.2.1 请求DTO

```java
// 购买订阅请求
public record PurchaseSubscriptionRequest(
    @NotNull SubscriptionType subscriptionType
) {}

// 购买商品请求
public record PurchaseProductRequest(
    @NotNull Long productId
) {}
```

#### 4.2.2 响应DTO

```java
// 会员信息响应
public record MembershipInfoResponse(
    Long userId,
    int currentPoints,
    MembershipType membershipType,
    VipLevel vipLevel,
    boolean hasMembership,
    LocalDateTime subscriptionExpireAt,
    String nextLevelInfo,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

// 积分历史响应
public record PointsHistoryResponse(
    Long id,
    int pointsChange,
    int pointsBefore,
    int pointsAfter,
    PointsChangeReason reason,
    String description,
    LocalDateTime createdAt
) {}

// 商品信息响应
public record ProductInfoResponse(
    Long id,
    String productName,
    ProductType productType,
    BigDecimal price,
    String description,
    boolean purchasable
) {}
```

### 4.3 API接口总结

| 端点 | 方法 | 权限 | 说明 |
|-----|------|------|------|
| `/api/membership/info` | GET | 无需权限 | 查询会员基本信息 |
| `/api/membership/points-history` | GET | 无需权限 | 查询积分历史 |
| `/api/membership/subscribe` | POST | MEMBERHIP_PURCHASE | 购买会员订阅 |
| `/api/membership/products` | GET | 无需权限 | 获取商品列表 |
| `/api/membership/products/purchase` | POST | PRODUCT_PURCHASE | 购买商品 |
| `/api/membership/products/{id}/use` | POST | PRODUCT_USE | 使用消耗性商品 |

## 5. 核心业务逻辑

### 5.1 定时任务设计

#### 5.1.1 MembershipScheduler

```java
@Component
@RequiredArgsConstructor
public class MembershipScheduler {
    
    private final MembershipService membershipService;
    
    // 每天凌晨1点执行
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void processDailyPointsChanges() {
        membershipService.processDailyPointsChange();
    }
}
```

#### 5.1.2 每日积分处理流程

1. **查询需要处理的会员：** 获取所有会员记录
2. **检查订阅到期：** 检查subscription_expire_at是否过期
3. **处理积分变化：**
   - 有会员资格：根据等级和类型增加积分
   - 无会员资格：根据等级和类型扣减积分
4. **记录历史：** 插入积分变化记录
5. **检查等级变化：** 根据新积分调整等级
6. **错误处理：** 记录失败用户，支持手动重试

### 5.2 核心业务方法

#### 5.2.1 会员初始化

```java
public void initializeMembership(Long userId) {
    // 用户注册时自动创建会员记录
    MembershipEntity membership = new MembershipEntity();
    membership.setUserId(userId);
    membership.setCurrentPoints(0);
    membership.setMembershipType(MembershipType.VIP);
    membership.setVipLevel(VipLevel.VIP1);
    membership.setHasMembership(false);
    membership.setCreatedAt(LocalDateTime.now());
    membership.setUpdatedAt(LocalDateTime.now());
    membershipMapper.insert(membership);
}
```

#### 5.2.2 购买订阅

```java
@Transactional
public void purchaseSubscription(Long userId, SubscriptionType type) {
    // 1. 计算订阅时长
    int days = switch (type) {
        case MONTHLY -> 30;
        case QUARTERLY -> 90;
        case YEARLY -> 365;
    };
    
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime expireTime = now.plusDays(days);
    
    // 2. 更新会员资格
    MembershipEntity membership = membershipMapper.findByUserId(userId);
    membership.setHasMembership(true);
    membership.setSubscriptionExpireAt(expireTime);
    membership.setUpdatedAt(now);
    membershipMapper.update(membership);
    
    // 3. 记录订阅历史
    MembershipSubscriptionEntity subscription = new MembershipSubscriptionEntity();
    subscription.setUserId(userId);
    subscription.setSubscriptionType(type);
    subscription.setStartTime(now);
    subscription.setExpireTime(expireTime);
    subscription.setCreatedAt(now);
    subscriptionMapper.insert(subscription);
}
```

#### 5.2.3 等级计算

```java
private VipLevel calculateLevelByPoints(int points) {
    if (points >= 50000) return VipLevel.VIP5;
    if (points >= 10001) return VipLevel.VIP4;
    if (points >= 3001) return VipLevel.VIP3;
    if (points >= 1001) return VipLevel.VIP2;
    return VipLevel.VIP1;
}

private int getBasePointsByLevel(VipLevel level) {
    return switch (level) {
        case VIP1 -> 1;
        case VIP2 -> 2;
        case VIP3 -> 3;
        case VIP4 -> 4;
        case VIP5 -> 5;
    };
}
```

#### 5.2.4 商品使用

```java
// 宝石等级加速卡效果配置
{
  "productName": "宝石等级加速卡",
  "productType": "CONSUMABLE",
  "price": 9.90,
  "effectConfig": {
    "type": "POINTS_BONUS",
    "points": 1000
  },
  "description": "使用后立即获得1000积分"
}

// 终身顶级卡效果配置
{
  "productName": "终身顶级卡", 
  "productType": "NON_CONSUMABLE",
  "price": 1999.00,
  "effectConfig": {
    "type": "MEMBERSHIP_UPGRADE",
    "membershipType": "SVIP"
  },
  "description": "购买后立即成为SVIP会员，积分增长翻倍"
}
```

## 6. 商品系统设计

### 6.1 商品类型

#### 6.1.1 宝石等级加速卡（消耗性）
- **价格：** 9.90元
- **效果：** 使用后立即获得1000积分
- **特点：** 可重复购买，购买后需要手动使用

#### 6.1.2 终身顶级卡（非消耗性）
- **价格：** 1999.00元
- **效果：** 购买后立即成为SVIP会员
- **特点：** 只能购买一次，购买后立即生效

### 6.2 商品购买流程

#### 6.2.1 消耗性商品
1. 用户购买商品 → 创建user_product记录（used=false）
2. 用户使用商品 → 标记used=true，应用商品效果
3. 根据商品效果更新积分/等级

#### 6.2.2 非消耗性商品
1. 检查用户是否已购买
2. 用户购买商品 → 创建user_product记录
3. 立即应用商品效果
4. 后续无法重复购买

## 7. 错误处理和边界情况

### 7.1 业务异常类型

```java
// 会员相关异常
"用户会员信息不存在"
"会员订阅已存在，请先到期后再购买"
"积分不足以执行此操作"

// 商品相关异常  
"商品不存在或已下架"
"消耗性商品已使用"
"非消耗性商品已购买，无法重复购买"
"用户余额不足"

// 订阅相关异常
"无效的订阅类型"
"订阅时间计算错误"
```

### 7.2 边界情况处理

#### 7.2.1 积分边界
- **积分不能为负：** `Math.max(0, oldPoints + change)`
- **积分上限：** 考虑设置合理上限（Integer.MAX_VALUE）

#### 7.2.2 等级边界
- **最低等级：** VIP1（积分 < 1000时保持VIP1）
- **最高等级：** VIP5（积分 >= 50000）
- **等级不能为null：** 初始化时默认VIP1

#### 7.2.3 订阅时间边界
- 订阅到期时间精确到秒
- 定时任务执行时检查过期
- 统一使用系统时区

#### 7.2.4 并发控制
- 使用`@Transactional`保证原子性
- 商品购买使用乐观锁防止超卖
- 定时任务使用独立线程池

### 7.3 定时任务容错

```java
// 分批处理，避免长事务
int batchSize = 100;

// 失败重试机制
int MAX_RETRY = 3;

// 记录失败用户ID，支持手动重试
```

## 8. 数据迁移

### 8.1 从旧系统迁移

#### 8.1.1 移除旧的MembershipLevel枚举
```java
// 删除：MembershipLevel.java (NORMAL, GOLD, DIAMOND)
```

#### 8.1.2 修改UserEntity
```sql
-- 移除app_user表中的membership_level字段
ALTER TABLE app_user DROP COLUMN membership_level;
```

#### 8.1.3 现有用户处理
```sql
-- 为现有用户创建会员记录
INSERT INTO membership (user_id, current_points, membership_type, vip_level, has_membership, created_at, updated_at)
SELECT id, 0, 'VIP', 'VIP1', FALSE, created_at, updated_at
FROM app_user
WHERE NOT EXISTS (SELECT 1 FROM membership WHERE membership.user_id = app_user.id);
```

## 9. 权限配置

### 9.1 新增权限

需要在`data.sql`中添加：

```sql
-- 新增权限
INSERT INTO app_permission(permission_code, description)
SELECT 'MEMBERSHIP_PURCHASE', '购买会员订阅'
WHERE NOT EXISTS (SELECT 1 FROM app_permission WHERE permission_code = 'MEMBERSHIP_PURCHASE');

INSERT INTO app_permission(permission_code, description)
SELECT 'PRODUCT_PURCHASE', '购买商品'
WHERE NOT EXISTS (SELECT 1 FROM app_permission WHERE permission_code = 'PRODUCT_PURCHASE');

INSERT INTO app_permission(permission_code, description)
SELECT 'PRODUCT_USE', '使用商品'
WHERE NOT EXISTS (SELECT 1 FROM app_permission WHERE permission_code = 'PRODUCT_USE');

-- 为USER角色分配权限
INSERT INTO role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM app_role r
JOIN app_permission p ON p.permission_code IN ('MEMBERSHIP_PURCHASE', 'PRODUCT_PURCHASE', 'PRODUCT_USE')
WHERE r.role_code = 'USER'
  AND NOT EXISTS (
      SELECT 1 FROM role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
```

### 9.2 公开端点配置

需要在`AuthInterceptor.preHandle`中添加公开端点：

```java
// 公开端点列表
if (path.equals("/api/auth/login") || path.equals("/api/users/register") ||
    path.equals("/api/membership/info") || path.equals("/api/membership/points-history") ||
    path.equals("/api/membership/products")) {
    return true;
}
```

## 10. 测试策略

### 10.1 单元测试

#### 10.1.1 MembershipService测试
```java
@ExtendWith(MockitoExtension.class)
class MembershipServiceTest {
    
    @Test
    void should_add_daily_points_for_vip_member() {
        // Given: VIP3用户
        // When: 执行每日积分增长
        // Then: 积分+3，历史记录正确
    }
    
    @Test  
    void should_add_doubled_points_for_svip_member() {
        // Given: SVIP VIP2用户
        // When: 执行每日积分增长  
        // Then: 积分+4 (2*2)
    }
    
    @Test
    void should_upgrade_level_when_points_reach_threshold() {
        // Given: VIP1用户，当前积分1000
        // When: 积分增加到1001
        // Then: 等级升级为VIP2
    }
    
    @Test
    void should_downgrade_level_when_points_drop_below_threshold() {
        // Given: VIP2用户，当前积分1000  
        // When: 积分减少到999
        // Then: 等级降级为VIP1
    }
    
    @Test
    void should_expire_subscription_when_time_passed() {
        // Given: 订阅到期的用户
        // When: 执行定时任务
        // Then: has_membership=false，开始反向扣减积分
    }
}
```

#### 10.1.2 ProductService测试
```java
@Test
void should_prevent_duplicate_non_consumable_purchase() {
    // 购买终身顶级卡后再购买应该失败
}

@Test
void should_apply_points_bonus_when_using_gem_card() {
    // 使用宝石等级加速卡应该增加1000积分
}
```

### 10.2 集成测试

```java
@SpringBootTest
@Sql("/schema.sql", "/data.sql")
class MembershipIntegrationTest {
    
    @Test
    void should_handle_full_membership_lifecycle() {
        // 1. 用户注册
        // 2. 购买月卡订阅
        // 3. 执行每日积分增长
        // 4. 购买宝石加速卡
        // 5. 等级升级
        // 6. 订阅到期
        // 7. 积分反向扣减
        // 8. 等级降级
    }
    
    @Test
    void should_handle_concurrent_points_update() {
        // 并发更新积分的测试
    }
}
```

## 11. 监控和日志

### 11.1 关键操作日志

```java
// 正常操作日志
log.info("User {} purchased {} subscription", userId, subscriptionType);
log.info("User {} points changed from {} to {}, reason: {}", userId, before, after, reason);
log.info("User {} purchased product {}", userId, productId);

// 警告日志
log.warn("Subscription expired for user {}", userId);
log.warn("Points would go negative for user {}, capped at 0", userId);

// 错误日志
log.error("Failed to process points change for user {}", userId, exception);
```

### 11.2 性能监控

```java
@Timed("membership.process_daily_points")
@Counted("membership.process_count")  
public void processDailyPointsChanges() {
    // 定时任务执行
}
```

## 12. 实施计划

### 12.1 实施阶段

1. **阶段1：数据库和基础架构**
   - 创建数据库表
   - 实现枚举和实体类
   - 配置MyBatis Mapper扫描

2. **阶段2：核心业务逻辑**
   - 实现MembershipService
   - 实现MembershipScheduler
   - 实现等级计算逻辑

3. **阶段3：商品系统**
   - 实现ProductService
   - 实现商品购买和使用逻辑

4. **阶段4：API接口**
   - 实现MembershipController
   - 实现DTO和验证

5. **阶段5：数据迁移**
   - 移除旧的MembershipLevel
   - 迁移现有用户数据

6. **阶段6：测试**
   - 单元测试
   - 集成测试

### 12.2 风险评估

**技术风险：**
- 定时任务可能影响性能 → 分批处理，使用独立线程池
- 并发积分更新 → 使用事务和乐观锁
- 数据迁移可能失败 → 提前备份，分步骤迁移

**业务风险：**
- 用户对积分扣减不理解 → 提前公告，清晰展示规则
- 定时任务执行时间 → 选择低峰期（凌晨1点）

## 13. 后续扩展

### 13.1 可能的功能扩展

1. **积分商城：** 用积分兑换虚拟商品
2. **会员特权：** 不同等级享受不同权益
3. **推荐奖励：** 邀请新用户获得积分
4. **积分任务：** 完成特定任务获得积分
5. **会员活动：** 限时双倍积分等

### 13.2 技术优化方向

1. **缓存优化：** Redis缓存会员信息
2. **异步处理：** 商品购买异步处理
3. **消息队列：** 积分变化事件驱动
4. **数据分析：** 会员行为分析

## 14. 总结

本设计文档详细描述了基于积分的会员系统的设计方案，包括：

- 完整的数据模型设计（5个核心表）
- 清晰的业务规则定义（VIP1-VIP5 + SVIP）
- 详细的API接口设计（6个核心接口）
- 健壮的错误处理机制
- 完整的测试策略

系统采用传统服务层架构，符合现有项目风格，具有良好的可维护性和可扩展性。通过定时任务实现每日积分自动增长/扣减，通过商品系统提供灵活的积分获取方式，为用户提供完整的会员体验。

