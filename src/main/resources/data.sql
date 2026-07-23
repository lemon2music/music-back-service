INSERT INTO app_role(role_code, role_name)
SELECT 'USER', '普通用户'
WHERE NOT EXISTS (SELECT 1 FROM app_role WHERE role_code = 'USER');

INSERT INTO app_role(role_code, role_name)
SELECT 'ADMIN', '管理员'
WHERE NOT EXISTS (SELECT 1 FROM app_role WHERE role_code = 'ADMIN');

INSERT INTO app_permission(permission_code, description)
SELECT 'USER_SELF', '用户查看自身信息和执行登出/注销'
WHERE NOT EXISTS (SELECT 1 FROM app_permission WHERE permission_code = 'USER_SELF');

INSERT INTO app_permission(permission_code, description)
SELECT 'USER_MANAGE_MEMBERSHIP', '管理用户会员等级'
WHERE NOT EXISTS (SELECT 1 FROM app_permission WHERE permission_code = 'USER_MANAGE_MEMBERSHIP');

INSERT INTO app_permission(permission_code, description)
SELECT 'USER_ASSIGN_ROLE', '分配用户角色'
WHERE NOT EXISTS (SELECT 1 FROM app_permission WHERE permission_code = 'USER_ASSIGN_ROLE');

INSERT INTO role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM app_role r
JOIN app_permission p ON p.permission_code = 'USER_SELF'
WHERE r.role_code = 'USER'
  AND NOT EXISTS (
      SELECT 1 FROM role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM app_role r
JOIN app_permission p ON p.permission_code IN ('USER_SELF', 'USER_MANAGE_MEMBERSHIP', 'USER_ASSIGN_ROLE')
WHERE r.role_code = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- ==================== 会员系统权限 ====================

INSERT INTO app_permission(permission_code, description)
SELECT 'MEMBERSHIP_PURCHASE', '购买会员订阅'
WHERE NOT EXISTS (SELECT 1 FROM app_permission WHERE permission_code = 'MEMBERSHIP_PURCHASE');

INSERT INTO app_permission(permission_code, description)
SELECT 'PRODUCT_PURCHASE', '购买商品'
WHERE NOT EXISTS (SELECT 1 FROM app_permission WHERE permission_code = 'PRODUCT_PURCHASE');

INSERT INTO app_permission(permission_code, description)
SELECT 'PRODUCT_USE', '使用商品'
WHERE NOT EXISTS (SELECT 1 FROM app_permission WHERE permission_code = 'PRODUCT_USE');

-- 普通用户可购买订阅、商品并使用商品
INSERT INTO role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM app_role r
JOIN app_permission p ON p.permission_code IN ('MEMBERSHIP_PURCHASE', 'PRODUCT_PURCHASE', 'PRODUCT_USE')
WHERE r.role_code = 'USER'
  AND NOT EXISTS (
      SELECT 1 FROM role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- 管理员同样拥有会员相关权限
INSERT INTO role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM app_role r
JOIN app_permission p ON p.permission_code IN ('MEMBERSHIP_PURCHASE', 'PRODUCT_PURCHASE', 'PRODUCT_USE')
WHERE r.role_code = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- ==================== 商品种子数据 ====================

-- 宝石等级加速卡：消耗性，使用后获得 1000 积分
INSERT INTO product(product_name, product_type, price, effect_config, description, status, created_at, updated_at)
SELECT '宝石等级加速卡', 'CONSUMABLE', 9.90,
       '{"type":"POINTS_BONUS","points":1000}',
       '使用后立即获得1000积分',
       'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM product WHERE product_name = '宝石等级加速卡');

-- 终身顶级卡：非消耗性，购买后成为 SVIP
INSERT INTO product(product_name, product_type, price, effect_config, description, status, created_at, updated_at)
SELECT '终身顶级卡', 'NON_CONSUMABLE', 1999.00,
       '{"type":"MEMBERSHIP_UPGRADE","membershipType":"SVIP"}',
       '购买后立即成为SVIP会员，积分增长翻倍',
       'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM product WHERE product_name = '终身顶级卡');
