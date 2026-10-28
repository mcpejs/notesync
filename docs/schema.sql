-- notesync 스키마 (MySQL 8)
-- 지금은 수동 실행. 배포 전에 Flyway 마이그레이션(V1__create_users.sql)으로 옮긴다.
-- 실행: notesync_app 사용자로 접속 → USE notesync;

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(20) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL
);
