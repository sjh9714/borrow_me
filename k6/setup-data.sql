-- =============================================================
-- BorrowMe k6 부하 테스트용 데이터 시딩
-- 실행: mysql -u root -p shop < k6/setup-data.sql
-- =============================================================

-- 기존 테스트 데이터 정리
DELETE FROM video_hashtags WHERE video_id IN (SELECT id FROM videos WHERE title LIKE 'k6_test_%');
DELETE FROM reservations WHERE video_id IN (SELECT id FROM videos WHERE title LIKE 'k6_test_%');
DELETE FROM follows WHERE follower_id IN (SELECT id FROM user WHERE email LIKE 'k6_%@test.com');
DELETE FROM follows WHERE followed_id IN (SELECT id FROM user WHERE email LIKE 'k6_%@test.com');
DELETE FROM recent_search WHERE user_id IN (SELECT id FROM user WHERE email LIKE 'k6_%@test.com');
DELETE FROM videos WHERE title LIKE 'k6_test_%';
DELETE FROM user_roles WHERE user_id IN (SELECT id FROM user WHERE email LIKE 'k6_%@test.com');
DELETE FROM user WHERE email LIKE 'k6_%@test.com';

-- =============================================================
-- 1. 사용자 생성
--    BCrypt hash of "TestPass123": $2a$10$U2DYvqsfASxgmfFzhUJ1BOvbVmIT6McE4Q8RFbx1cwrkKk18.ptHu
--    NOTE: username = email (로그인 시 findByUsername + findByEmail 모두 사용하므로)
-- =============================================================

-- 상품 소유자 (2명)
INSERT INTO user (username, email, password_hash, email_verified, created_at, updated_at) VALUES
('k6_owner1@test.com', 'k6_owner1@test.com', '$2a$10$U2DYvqsfASxgmfFzhUJ1BOvbVmIT6McE4Q8RFbx1cwrkKk18.ptHu', true, NOW(), NOW()),
('k6_owner2@test.com', 'k6_owner2@test.com', '$2a$10$U2DYvqsfASxgmfFzhUJ1BOvbVmIT6McE4Q8RFbx1cwrkKk18.ptHu', true, NOW(), NOW());

-- 예약자/테스터 (100명) - 프로시저로 생성
DELIMITER //
DROP PROCEDURE IF EXISTS create_k6_users//
CREATE PROCEDURE create_k6_users()
BEGIN
  DECLARE i INT DEFAULT 1;
  WHILE i <= 100 DO
    INSERT INTO user (username, email, password_hash, email_verified, created_at, updated_at)
    VALUES (
      CONCAT('k6_user', LPAD(i, 3, '0'), '@test.com'),
      CONCAT('k6_user', LPAD(i, 3, '0'), '@test.com'),
      '$2a$10$U2DYvqsfASxgmfFzhUJ1BOvbVmIT6McE4Q8RFbx1cwrkKk18.ptHu',
      true, NOW(), NOW()
    );
    SET i = i + 1;
  END WHILE;
END//
DELIMITER ;
CALL create_k6_users();
DROP PROCEDURE IF EXISTS create_k6_users;

-- ROLE_USER 부여
INSERT INTO user_roles (user_id, roles)
SELECT id, 'ROLE_USER' FROM user WHERE email LIKE 'k6_%@test.com';

-- =============================================================
-- 2. 해시태그 생성
-- =============================================================
INSERT IGNORE INTO hashtags (name) VALUES
  ('sports'), ('cycling'), ('camping'), ('hiking'), ('fitness'),
  ('photography'), ('cooking'), ('music'), ('gaming'), ('reading'),
  ('travel'), ('electronics'), ('tools'), ('outdoor'), ('indoor'),
  ('winter'), ('summer'), ('premium'), ('budget'), ('popular');

-- =============================================================
-- 3. 상품 생성
-- =============================================================

-- 동시 예약 테스트용 상품 (owner1 소유, 재고 50)
INSERT INTO videos (user_id, title, description, video_url, total_quantity, available_quantity, reservation_status, created_at, updated_at)
SELECT id, 'k6_test_concurrent_reserve', '동시 예약 테스트용 상품 #sports #popular', 'k6-test-image.jpg', 50, 50, 'AVAILABLE', NOW(), NOW()
FROM user WHERE email = 'k6_owner1@test.com';

-- owner1의 상품 50개
DELIMITER //
DROP PROCEDURE IF EXISTS create_k6_products_owner1//
CREATE PROCEDURE create_k6_products_owner1()
BEGIN
  DECLARE i INT DEFAULT 1;
  DECLARE owner_id BIGINT;
  SELECT id INTO owner_id FROM user WHERE email = 'k6_owner1@test.com';
  WHILE i <= 50 DO
    INSERT INTO videos (user_id, title, description, video_url, total_quantity, available_quantity, reservation_status, created_at, updated_at)
    VALUES (owner_id, CONCAT('k6_test_product_', i), CONCAT('테스트 상품 설명 ', i, ' #sports #camping'), 'k6-test-image.jpg', 10, 10, 'AVAILABLE', NOW(), NOW());
    SET i = i + 1;
  END WHILE;
END//
DELIMITER ;
CALL create_k6_products_owner1();
DROP PROCEDURE IF EXISTS create_k6_products_owner1;

-- owner2의 상품 50개
DELIMITER //
DROP PROCEDURE IF EXISTS create_k6_products_owner2//
CREATE PROCEDURE create_k6_products_owner2()
BEGIN
  DECLARE i INT DEFAULT 51;
  DECLARE owner_id BIGINT;
  SELECT id INTO owner_id FROM user WHERE email = 'k6_owner2@test.com';
  WHILE i <= 100 DO
    INSERT INTO videos (user_id, title, description, video_url, total_quantity, available_quantity, reservation_status, created_at, updated_at)
    VALUES (owner_id, CONCAT('k6_test_product_', i), CONCAT('테스트 상품 설명 ', i, ' #hiking #photography'), 'k6-test-image.jpg', 10, 10, 'AVAILABLE', NOW(), NOW());
    SET i = i + 1;
  END WHILE;
END//
DELIMITER ;
CALL create_k6_products_owner2();
DROP PROCEDURE IF EXISTS create_k6_products_owner2;

-- =============================================================
-- 4. 상품-해시태그 관계
-- =============================================================
INSERT INTO video_hashtags (video_id, hashtag_id)
SELECT v.id, h.id FROM videos v, hashtags h
WHERE v.title LIKE 'k6_test_%' AND h.name = 'popular';

INSERT INTO video_hashtags (video_id, hashtag_id)
SELECT v.id, h.id FROM videos v, hashtags h
WHERE v.title LIKE 'k6_test_%' AND h.name = 'sports' AND v.id % 2 = 0;

INSERT INTO video_hashtags (video_id, hashtag_id)
SELECT v.id, h.id FROM videos v, hashtags h
WHERE v.title LIKE 'k6_test_%' AND h.name = 'camping' AND v.id % 3 = 0;

INSERT INTO video_hashtags (video_id, hashtag_id)
SELECT v.id, h.id FROM videos v, hashtags h
WHERE v.title LIKE 'k6_test_%' AND h.name = 'hiking' AND v.id % 4 = 0;

INSERT INTO video_hashtags (video_id, hashtag_id)
SELECT v.id, h.id FROM videos v, hashtags h
WHERE v.title LIKE 'k6_test_%' AND h.name = 'photography' AND v.id % 5 = 0;

-- =============================================================
-- 5. 팔로우 관계
-- =============================================================
INSERT INTO follows (follower_id, followed_id, created_at)
SELECT u.id, o.id, NOW()
FROM user u, user o
WHERE u.email LIKE 'k6_user0%'
AND CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, 'k6_user', -1), '@', 1) AS UNSIGNED) <= 30
AND o.email = 'k6_owner1@test.com';

INSERT INTO follows (follower_id, followed_id, created_at)
SELECT u.id, o.id, NOW()
FROM user u, user o
WHERE u.email LIKE 'k6_user0%'
AND CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, 'k6_user', -1), '@', 1) AS UNSIGNED) BETWEEN 31 AND 60
AND o.email = 'k6_owner2@test.com';

-- =============================================================
-- 확인
-- =============================================================
SELECT '--- k6 테스트 데이터 시딩 완료 ---' AS status;
SELECT 'Users' AS entity, COUNT(*) AS count FROM user WHERE email LIKE 'k6_%@test.com'
UNION ALL
SELECT 'Products', COUNT(*) FROM videos WHERE title LIKE 'k6_test_%'
UNION ALL
SELECT 'Hashtags', COUNT(*) FROM hashtags
UNION ALL
SELECT 'Video-Hashtag Links', COUNT(*) FROM video_hashtags vh JOIN videos v ON vh.video_id = v.id WHERE v.title LIKE 'k6_test_%'
UNION ALL
SELECT 'Follows', COUNT(*) FROM follows f JOIN user u ON f.follower_id = u.id WHERE u.email LIKE 'k6_%@test.com';
