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
