-- ============================================================
-- V2: 初始化角色和管理员账号
-- ============================================================

-- 4 个角色
INSERT INTO `role` (name, permissions) VALUES
('system', '["*"]'),
('admin', '["dashboard","schedule","appointment","finance","report","physical","settings","system"]'),
('doctor', '["dashboard","schedule","appointment","report"]'),
('nurse', '["dashboard","schedule","appointment","report","physical","system"]');

-- 默认管理员账号（密码均为 admin123）
INSERT INTO `admin` (username, password_hash, role_id, phone) VALUES
('admin', '$2a$10$ElqwOOfi6X4JzUCTzWRaL.FfZYDgDdmwxKYe8haribeTkevVl2T42', 2, '13800000001'),
('system', '$2a$10$ElqwOOfi6X4JzUCTzWRaL.FfZYDgDdmwxKYe8haribeTkevVl2T42', 1, '13800000002'),
('doctor', '$2a$10$ElqwOOfi6X4JzUCTzWRaL.FfZYDgDdmwxKYe8haribeTkevVl2T42', 3, '13800000003'),
('nurse', '$2a$10$ElqwOOfi6X4JzUCTzWRaL.FfZYDgDdmwxKYe8haribeTkevVl2T42', 4, '13800000004');
