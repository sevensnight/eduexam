-- MySQL schema for eduexam
-- Run this once to initialize the database: mysql -u root -p < schema.sql

CREATE DATABASE IF NOT EXISTS eduexam DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE eduexam;

CREATE TABLE IF NOT EXISTS users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL UNIQUE,
    email           VARCHAR(128) NOT NULL UNIQUE,
    hashed_password VARCHAR(256) NOT NULL,
    role            VARCHAR(16)  NOT NULL DEFAULT 'student' COMMENT 'admin/teacher/student',
    real_name       VARCHAR(64),
    class_name      VARCHAR(64),
    is_active       TINYINT(1)   NOT NULL DEFAULT 1,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_role (role),
    INDEX idx_users_class (class_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS categories (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    description TEXT         NULL,
    parent_id   BIGINT       NULL REFERENCES categories(id),
    creator_id  BIGINT       NULL REFERENCES users(id),
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_categories_parent (parent_id),
    INDEX idx_categories_creator (creator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tags (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL UNIQUE,
    color       VARCHAR(16)  NOT NULL DEFAULT '#409EFF',
    creator_id  BIGINT       NULL REFERENCES users(id),
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tags_creator (creator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS questions (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    title         TEXT         NOT NULL,
    question_type VARCHAR(16)  NOT NULL COMMENT 'single/multiple/truefalse/essay',
    difficulty    VARCHAR(16)  NOT NULL DEFAULT 'medium' COMMENT 'easy/medium/hard',
    options       TEXT         NULL COMMENT 'JSON array for single/multiple',
    answer        TEXT         NOT NULL,
    explanation   TEXT         NULL,
    category_id   BIGINT       NULL REFERENCES categories(id),
    creator_id    BIGINT       NULL REFERENCES users(id),
    usage_count   INT          NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_questions_category (category_id),
    INDEX idx_questions_creator (creator_id),
    INDEX idx_questions_type (question_type),
    INDEX idx_questions_difficulty (difficulty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS question_tags (
    question_id BIGINT NOT NULL REFERENCES questions(id),
    tag_id      BIGINT NOT NULL REFERENCES tags(id),
    PRIMARY KEY (question_id, tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS courses (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    description TEXT         NULL,
    teacher_id  BIGINT       NULL REFERENCES users(id),
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_courses_teacher (teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS course_enrollments (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id   BIGINT   NOT NULL REFERENCES courses(id),
    student_id  BIGINT   NOT NULL REFERENCES users(id),
    enrolled_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_enrollment (course_id, student_id),
    INDEX idx_enrollment_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS exams (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    title            VARCHAR(256) NOT NULL,
    description      TEXT         NULL,
    creator_id       BIGINT       NULL REFERENCES users(id),
    course_id        BIGINT       NULL REFERENCES courses(id),
    target_class     VARCHAR(64)  NULL,
    status           VARCHAR(16)  NOT NULL DEFAULT 'draft' COMMENT 'draft/published/closed',
    open_time        DATETIME     NULL,
    close_time       DATETIME     NULL,
    duration_minutes INT          NULL COMMENT 'minutes',
    max_attempts     INT          NULL,
    allow_retake TINYINT(1)   NOT NULL DEFAULT 0,
    score_public TINYINT(1)   NOT NULL DEFAULT 1,
    total_score  DOUBLE       NOT NULL DEFAULT 0,
    pass_score   DOUBLE       NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_exams_creator (creator_id),
    INDEX idx_exams_status (status),
    INDEX idx_exams_open_time (open_time),
    INDEX idx_exams_close_time (close_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS exam_questions (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    exam_id     BIGINT  NOT NULL REFERENCES exams(id),
    question_id BIGINT  NOT NULL REFERENCES questions(id),
    order_num   INT     NOT NULL DEFAULT 1,
    score       DOUBLE  NOT NULL DEFAULT 10,
    UNIQUE KEY uq_exam_question (exam_id, question_id),
    INDEX idx_eq_exam (exam_id),
    INDEX idx_eq_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS exam_records (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    exam_id       BIGINT       NOT NULL REFERENCES exams(id),
    student_id    BIGINT       NOT NULL REFERENCES users(id),
    status        VARCHAR(16)  NOT NULL DEFAULT 'in_progress' COMMENT 'in_progress/submitted/graded',
    started_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at  DATETIME     NULL,
    total_score   DOUBLE       NULL,
    version       INT          NOT NULL DEFAULT 1,
    attempt_count INT          NOT NULL DEFAULT 1,
    essay_graded  TINYINT(1)   NOT NULL DEFAULT 0,
    INDEX idx_records_exam (exam_id),
    INDEX idx_records_student (student_id),
    INDEX idx_records_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS answers (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id   BIGINT      NOT NULL REFERENCES exam_records(id),
    question_id BIGINT      NOT NULL REFERENCES questions(id),
    user_answer TEXT        NULL,
    is_correct  VARCHAR(1)  NULL COMMENT 'Y/N/P',
    score_got   DOUBLE      NOT NULL DEFAULT 0,
    INDEX idx_answers_record (record_id),
    INDEX idx_answers_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wrong_book (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id  BIGINT   NOT NULL REFERENCES users(id),
    question_id BIGINT   NOT NULL REFERENCES questions(id),
    added_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_wrong_book (student_id, question_id),
    INDEX idx_wb_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notifications (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id),
    content    TEXT         NOT NULL,
    type       VARCHAR(32)  NOT NULL DEFAULT 'info',
    is_read    TINYINT(1)   NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_notif_user (user_id),
    INDEX idx_notif_read (is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
