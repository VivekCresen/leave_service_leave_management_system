
CREATE SCHEMA IF NOT EXISTS user_schema;
CREATE SCHEMA IF NOT EXISTS email_schema;
CREATE SCHEMA IF NOT EXISTS leave_schema;

-- ============================================================
-- core → user_schema
-- ============================================================
CREATE TABLE IF NOT EXISTS user_schema.role (
    id          BIGSERIAL PRIMARY KEY,
    role_name   VARCHAR UNIQUE,
    unique_name VARCHAR UNIQUE,
    role_desc   VARCHAR,
    create_date TIMESTAMP WITH TIME ZONE,
    update_date TIMESTAMP WITH TIME ZONE,
    created_by  VARCHAR,
    updated_by  VARCHAR
);

CREATE TABLE IF NOT EXISTS user_schema.user_profile (
    id          BIGSERIAL PRIMARY KEY,
    company_id  VARCHAR(100),
    user_name   VARCHAR UNIQUE,
    full_name   VARCHAR(255),
    email_id    VARCHAR(200) UNIQUE,
    user_pswd   VARCHAR(255),
    role        VARCHAR,
    role_id     BIGINT,
    active      BOOLEAN NOT NULL,
    create_date TIMESTAMP WITH TIME ZONE,
    update_date TIMESTAMP WITH TIME ZONE,
    created_by  VARCHAR(200),
    updated_by  VARCHAR(200),
    last_login  TIMESTAMP WITH TIME ZONE,
    gender      VARCHAR,
    CONSTRAINT fk_user_role
        FOREIGN KEY (role_id) REFERENCES user_schema.role(id)
        ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS user_schema.otp (
    id          BIGSERIAL PRIMARY KEY,
    email_id    VARCHAR UNIQUE,
    otp_code    VARCHAR,
    expiry_time TIMESTAMP,
    user_id     BIGINT,
    CONSTRAINT fk_otp_user
        FOREIGN KEY (user_id) REFERENCES user_schema.user_profile(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_otp_email_id    ON user_schema.otp(email_id);
CREATE INDEX IF NOT EXISTS idx_otp_expiry_time ON user_schema.otp(expiry_time);

-- ============================================================
-- core → email_schema
-- ============================================================
CREATE TABLE IF NOT EXISTS email_schema.email_configuration (
    id               BIGSERIAL PRIMARY KEY,
    host             VARCHAR(255) NOT NULL,
    port             INTEGER      NOT NULL,
    username         VARCHAR(255),
    password         VARCHAR(255),
    protocol         VARCHAR(50)  NOT NULL DEFAULT 'smtp',
    auth             BOOLEAN      NOT NULL DEFAULT TRUE,
    starttls_enabled BOOLEAN      NOT NULL DEFAULT TRUE,
    ssl_enabled      BOOLEAN      NOT NULL DEFAULT FALSE,
    from_address     VARCHAR(255) NOT NULL,
    logo_path        VARCHAR(500),
    batch_size       INTEGER      NOT NULL DEFAULT 25,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- core → leave_schema
-- ============================================================
CREATE TABLE IF NOT EXISTS leave_schema.leave_types (
    id                 SERIAL PRIMARY KEY,
    leave_name         VARCHAR,
    leave_unique_name  VARCHAR,
    description        VARCHAR,
    max_days           INTEGER,
    gender_restriction VARCHAR,
    created_at         TIMESTAMP WITH TIME ZONE,
    updated_at         TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_leave_types_name        ON leave_schema.leave_types(leave_name);
CREATE INDEX IF NOT EXISTS idx_leave_types_unique_name ON leave_schema.leave_types(leave_unique_name);

CREATE TABLE IF NOT EXISTS leave_schema.employee_leave (
    id       BIGSERIAL PRIMARY KEY,
    user_id  BIGINT UNIQUE,
    full_name VARCHAR,
    email_id VARCHAR UNIQUE,
    leaves   JSONB,
    gender   VARCHAR,
    CONSTRAINT fk_employee_user
        FOREIGN KEY (user_id) REFERENCES user_schema.user_profile(id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS leave_schema.leave_application (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT  NOT NULL,
    leave_type_id       INTEGER,
    leave_type          VARCHAR,
    email_id            VARCHAR,
    reason              VARCHAR,
    comments            VARCHAR,
    status              VARCHAR  DEFAULT 'PENDING',
    approved_by         VARCHAR,
    rejection_reason    VARCHAR,
    reminder_sent_flags VARCHAR,
    trail               JSONB    DEFAULT '[]'::jsonb,
    editable            BOOLEAN  DEFAULT TRUE,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT fk_leave_app_user
        FOREIGN KEY (user_id) REFERENCES user_schema.user_profile(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_leave_app_type
        FOREIGN KEY (leave_type_id) REFERENCES leave_schema.leave_types(id)
        ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_leave_application_user_id      ON leave_schema.leave_application(user_id);
CREATE INDEX IF NOT EXISTS idx_leave_application_leave_type_id ON leave_schema.leave_application(leave_type_id);
CREATE INDEX IF NOT EXISTS idx_leave_application_trail        ON leave_schema.leave_application USING GIN (trail);

CREATE TABLE IF NOT EXISTS leave_schema.leave_notify_users (
    id                   BIGSERIAL PRIMARY KEY,
    leave_application_id BIGINT NOT NULL,
    user_id              BIGINT NOT NULL,
    CONSTRAINT fk_notify_leave_app
        FOREIGN KEY (leave_application_id) REFERENCES leave_schema.leave_application(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_notify_user
        FOREIGN KEY (user_id) REFERENCES user_schema.user_profile(id)
        ON DELETE CASCADE,
    CONSTRAINT uq_leave_notify_user
        UNIQUE (leave_application_id, user_id)
);

CREATE TABLE IF NOT EXISTS leave_schema.leave_dates (
    id                   BIGSERIAL PRIMARY KEY,
    leave_application_id BIGINT NOT NULL,
    leave_date           DATE   NOT NULL,
    day_type             VARCHAR DEFAULT 'FULL',
    CONSTRAINT fk_leave_dates_app
        FOREIGN KEY (leave_application_id) REFERENCES leave_schema.leave_application(id)
        ON DELETE CASCADE,
    CONSTRAINT uq_leave_date_per_app
        UNIQUE (leave_application_id, leave_date)
);

CREATE INDEX IF NOT EXISTS idx_leave_dates_application_id ON leave_schema.leave_dates(leave_application_id);

CREATE TABLE IF NOT EXISTS leave_schema.public_holidays (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR  NOT NULL,
    holiday_date DATE     NOT NULL,
    description  VARCHAR,
    created_by   VARCHAR,
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_holiday_date ON leave_schema.public_holidays(holiday_date);

-- ============================================================
-- SEED DATA
-- ============================================================
INSERT INTO user_schema.role (role_name, unique_name, role_desc, create_date, update_date, created_by, updated_by)
VALUES
    ('Administrator', 'ADMIN',    'System administrator with full access',        NOW(), NOW(), 'system', 'system'),
    ('Manager',       'MANAGER',  'Manager who can review team leave requests',   NOW(), NOW(), 'system', 'system'),
    ('Employee',      'EMPLOYEE', 'Employee who can apply for leave',             NOW(), NOW(), 'system', 'system')
ON CONFLICT DO NOTHING;

INSERT INTO user_schema.user_profile
    (company_id, user_name, full_name, email_id, user_pswd, role, role_id, active, create_date, update_date, created_by, updated_by, gender)
VALUES
    ('CRESEN001', 'admin',    'System Admin',  'admin@cresen.com',
     '$2a$10$pJ.fQAMc8nCtbLatctLTJe9fnFsFrAg619UjcNuDcjYOxwflZ9ZWq',
     'ADMIN',    (SELECT id FROM user_schema.role WHERE unique_name = 'ADMIN'),
     TRUE, NOW(), NOW(), 'system', 'system', 'Male'),
    ('CRESEN001', 'manager',  'Team Manager',  'manager@cresen.com',
     '$2a$10$9J66OXttbcMxSPDuBvEM2.hqpWtz25tasFjNzyxVIOM2qIiHTbG1W',
     'MANAGER',  (SELECT id FROM user_schema.role WHERE unique_name = 'MANAGER'),
     TRUE, NOW(), NOW(), 'system', 'system', 'Female'),
    ('CRESEN001', 'employee', 'Demo Employee', 'employee@cresen.com',
     '$2a$10$Sc/w16h0rP3LYf2JQqpczOZ3ANUdhtmkPpbDXpJ4jlub6Zx06Mta.',
     'EMPLOYEE', (SELECT id FROM user_schema.role WHERE unique_name = 'EMPLOYEE'),
     TRUE, NOW(), NOW(), 'system', 'system', 'Male')
ON CONFLICT DO NOTHING;

INSERT INTO email_schema.email_configuration
    (host, port, username, password, protocol, auth, starttls_enabled, ssl_enabled, from_address, logo_path, batch_size, active)
SELECT 'smtp.gmail.com', 587, 'viveksinhchavda@gmail.com', 'drauzuufuzlddfwy',
       'smtp', TRUE, TRUE, FALSE, 'viveksinhchavda@gmail.com',
       '/home/vivek/Documents/CresenProject/front-end/frontend_leave_management_system/public/assets/logo-6.png',
       25, TRUE
WHERE NOT EXISTS (SELECT 1 FROM email_schema.email_configuration WHERE active = TRUE);

INSERT INTO leave_schema.leave_types (leave_name, leave_unique_name, description, max_days, created_at, updated_at)
SELECT 'Annual Leave', 'ANNUAL_LEAVE', 'Planned vacation or personal time off.', 21, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM leave_schema.leave_types WHERE leave_unique_name = 'ANNUAL_LEAVE');

INSERT INTO leave_schema.leave_types (leave_name, leave_unique_name, description, max_days, created_at, updated_at)
SELECT 'Sick Leave', 'SICK_LEAVE', 'Medical leave for sickness or recovery.', 12, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM leave_schema.leave_types WHERE leave_unique_name = 'SICK_LEAVE');

INSERT INTO leave_schema.leave_types (leave_name, leave_unique_name, description, max_days, created_at, updated_at)
SELECT 'Casual Leave', 'CASUAL_LEAVE', 'Short notice leave for urgent personal work.', 7, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM leave_schema.leave_types WHERE leave_unique_name = 'CASUAL_LEAVE');

INSERT INTO leave_schema.leave_types (leave_name, leave_unique_name, description, max_days, gender_restriction, created_at, updated_at)
SELECT 'Maternity Leave', 'MATERNITY_LEAVE', 'Extended leave for childbirth and recovery.', 180, 'FEMALE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM leave_schema.leave_types WHERE leave_unique_name = 'MATERNITY_LEAVE');

INSERT INTO leave_schema.leave_types (leave_name, leave_unique_name, description, max_days, gender_restriction, created_at, updated_at)
SELECT 'Paternity Leave', 'PATERNITY_LEAVE', 'Leave to support a newborn child and family.', 15, 'MALE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM leave_schema.leave_types WHERE leave_unique_name = 'PATERNITY_LEAVE');
