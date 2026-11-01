-- notesync 스키마 (MySQL 8)
-- 지금은 수동 실행. 배포 전에 Flyway 마이그레이션(V1__create_users.sql)으로 옮긴다.
-- 실행: notesync_app 사용자로 접속 → USE notesync;

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(20) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL
);

CREATE TABLE documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    current_version_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_documents_users FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    num INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    client_request_id CHAR(36) NOT NULL UNIQUE,
    CONSTRAINT fk_versions_documents FOREIGN KEY (document_id) REFERENCES documents(id),
    CONSTRAINT uc_versions_document_id_num UNIQUE (document_id, num)
);

CREATE TABLE chunks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content_hash BINARY(32) NOT NULL,
    content BLOB NOT NULL,
    user_id BIGINT NOT NULL,
    CONSTRAINT fk_chunks_users FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uc_chunks_user_id_content_hash UNIQUE (user_id, content_hash)
);

CREATE TABLE version_chunks (
    version_id BIGINT NOT NULL,
    pos INT NOT NULL,
    chunk_id BIGINT NOT NULL,
    CONSTRAINT fk_version_chunks_versions FOREIGN KEY (version_id) REFERENCES versions(id),
    CONSTRAINT fk_version_chunks_chunks FOREIGN KEY (chunk_id) REFERENCES chunks(id),
    CONSTRAINT PRIMARY KEY (version_id, pos)
);

ALTER TABLE documents ADD CONSTRAINT fk_documents_versions FOREIGN KEY (current_version_id) REFERENCES versions(id);
