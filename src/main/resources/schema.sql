CREATE TABLE IF NOT EXISTS app_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    membership_level VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    phone VARCHAR(20) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_app_user_phone UNIQUE (phone)
);

CREATE TABLE IF NOT EXISTS app_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_code VARCHAR(64) NOT NULL UNIQUE,
    role_name VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS app_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    permission_code VARCHAR(64) NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY(user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES app_role(id)
);

CREATE TABLE IF NOT EXISTS role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY(role_id, permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES app_role(id),
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES app_permission(id)
);

-- ==================== 会员系统 ====================

-- 会员信息表（每个用户一条）
CREATE TABLE IF NOT EXISTS membership (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    current_points INT NOT NULL DEFAULT 0,
    membership_type VARCHAR(16) NOT NULL,
    vip_level VARCHAR(8) NOT NULL,
    has_membership BOOLEAN NOT NULL DEFAULT FALSE,
    subscription_expire_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_membership_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

-- 积分变化历史表
CREATE TABLE IF NOT EXISTS membership_points_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    points_change INT NOT NULL,
    points_before INT NOT NULL,
    points_after INT NOT NULL,
    change_reason VARCHAR(32) NOT NULL,
    description VARCHAR(255) NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_points_history_user_created (user_id, created_at),
    CONSTRAINT fk_points_history_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

-- 会员订阅记录表
CREATE TABLE IF NOT EXISTS membership_subscription (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    subscription_type VARCHAR(16) NOT NULL,
    start_time DATETIME NOT NULL,
    expire_time DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_subscription_user_start (user_id, start_time),
    CONSTRAINT fk_subscription_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

-- 商品表
CREATE TABLE IF NOT EXISTS product (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_name VARCHAR(64) NOT NULL,
    product_type VARCHAR(16) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    effect_config TEXT NOT NULL,
    description VARCHAR(255) NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

-- 用户购买/持有商品记录表
CREATE TABLE IF NOT EXISTS user_product (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    purchase_time DATETIME NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    used_time DATETIME NULL,
    INDEX idx_user_product_user_purchase (user_id, purchase_time),
    CONSTRAINT fk_user_product_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_user_product_product FOREIGN KEY (product_id) REFERENCES product(id)
);

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
    cancelled_at DATETIME NULL,
    cancel_reason VARCHAR(255) NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_iap_order_no UNIQUE (order_no),
    INDEX idx_iap_order_user (user_id, created_at)
);

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

-- ==================== IAP 订单取消功能迁移 ====================
-- 为已存在的旧数据库（iap_order 表无以下两列）补充 cancelled_at / cancel_reason。
--
-- 幂等性说明：
-- - 新建库：CREATE TABLE IF NOT EXISTS iap_order 已包含这两列；ALTER ... ADD COLUMN IF NOT EXISTS 检测到列已存在会跳过，不报错。
-- - 旧库：CREATE TABLE IF NOT EXISTS 命中已存在的表（no-op），由以下 ALTER 补列；再次启动时 IF NOT EXISTS 仍为 no-op。
-- - 由于 spring.sql.init.mode: always 每次启动都执行此文件，必须保证语句幂等，因此使用 IF NOT EXISTS（不依赖 continue-on-error 容错）。
ALTER TABLE iap_order ADD COLUMN IF NOT EXISTS cancelled_at DATETIME NULL;
ALTER TABLE iap_order ADD COLUMN IF NOT EXISTS cancel_reason VARCHAR(255) NULL;
