-- 测试取消订单功能的数据准备脚本
--
-- 幂等性说明：
--   @Sql 默认以 ISOLATED 事务模式执行（独立事务并提交），因此不同测试方法间
--   这些数据可能残留。脚本开头的 DELETE 保证每次运行都从干净状态开始，
--   同时配合测试类上的 @Transactional 回滚 cancelOrder 的变更。
DELETE FROM iap_order WHERE order_no LIKE 'IAP_TEST_%';

-- 待支付订单（用于"成功取消"场景）
INSERT INTO iap_order (order_no, user_id, iap_product_id, huawei_product_id, amount, currency, status,
    huawei_purchase_order_id, huawei_purchase_token, created_at, paid_at, fulfilled_at, updated_at,
    cancelled_at, cancel_reason)
VALUES ('IAP_TEST_001', 1001, 1, 'com.test.product1', 9.99, 'CNY', 'PENDING', NULL, NULL,
    CURRENT_TIMESTAMP, NULL, NULL, CURRENT_TIMESTAMP, NULL, NULL);

-- 已支付订单（用于"状态不允许取消"场景）
INSERT INTO iap_order (order_no, user_id, iap_product_id, huawei_product_id, amount, currency, status,
    huawei_purchase_order_id, huawei_purchase_token, created_at, paid_at, fulfilled_at, updated_at,
    cancelled_at, cancel_reason)
VALUES ('IAP_TEST_002', 1001, 1, 'com.test.product1', 9.99, 'CNY', 'PAID', 'HWPAY_001', 'token123',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, NULL, NULL);

-- 另一个待支付订单（用于 null 取消原因 → 默认值场景）
INSERT INTO iap_order (order_no, user_id, iap_product_id, huawei_product_id, amount, currency, status,
    huawei_purchase_order_id, huawei_purchase_token, created_at, paid_at, fulfilled_at, updated_at,
    cancelled_at, cancel_reason)
VALUES ('IAP_TEST_003', 1001, 1, 'com.test.product1', 9.99, 'CNY', 'PENDING', NULL, NULL,
    CURRENT_TIMESTAMP, NULL, NULL, CURRENT_TIMESTAMP, NULL, NULL);
