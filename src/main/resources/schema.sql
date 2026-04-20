
CREATE SCHEMA IF NOT EXISTS user_schema;
CREATE SCHEMA IF NOT EXISTS email_schema;
CREATE SCHEMA IF NOT EXISTS leave_schema;

-- Add manager_approved_by column if upgrading existing DB
ALTER TABLE leave_schema.leave_application ADD COLUMN IF NOT EXISTS manager_approved_by VARCHAR;

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

-- template_type values (leave-service): PENDING_APPROVAL | LEAVE_APPROVED | LEAVE_REJECTED | REMINDER_4DAY | REMINDER_2DAY
-- template_type values (user-service):  PASSWORD_RESET_OTP | USER_CREATED | USER_DELETED | USER_ROLE_CHANGED
CREATE TABLE IF NOT EXISTS email_schema.email_template (
    id            BIGSERIAL PRIMARY KEY,
    template_type VARCHAR(50)  NOT NULL UNIQUE,
    subject       VARCHAR(500) NOT NULL,
    body_html     TEXT         NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
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
    manager_approved_by VARCHAR,
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

-- ============================================================
-- SEED: email templates
-- ============================================================
INSERT INTO email_schema.email_template (template_type, subject, body_html, active)
SELECT 'PENDING_APPROVAL',
       'Leave Approval Required',
       '<p style="margin:0 0 12px;color:#475569;font-size:15px;line-height:1.7;">Hello <strong>{{managerName}}</strong> <span style="color:#64748b;font-size:13px;">({{managerRole}})</span>,</p>
<p style="margin:0 0 18px;color:#475569;font-size:15px;line-height:1.7;">A new leave request has been submitted and is awaiting your approval.</p>
<table role="presentation" style="width:100%;border-collapse:separate;border-spacing:0 10px;margin:0 0 18px;">
  <tr>
    <td style="width:38%;padding:12px 14px;border-radius:14px 0 0 14px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Employee</td>
    <td style="padding:12px 14px;border-radius:0 14px 14px 0;background:#ffffff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">{{employeeName}}</td>
  </tr>
  <tr>
    <td style="padding:12px 14px;border-radius:14px 0 0 14px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Employee Role</td>
    <td style="padding:12px 14px;border-radius:0 14px 14px 0;background:#ffffff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">{{employeeRole}}</td>
  </tr>
  <tr>
    <td style="padding:12px 14px;border-radius:14px 0 0 14px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Leave Type</td>
    <td style="padding:12px 14px;border-radius:0 14px 14px 0;background:#ffffff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">{{leaveType}}</td>
  </tr>
  <tr>
    <td style="padding:12px 14px;border-radius:14px 0 0 14px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Reason</td>
    <td style="padding:12px 14px;border-radius:0 14px 14px 0;background:#ffffff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">{{reason}}</td>
  </tr>
</table>
{{datesTable}}
<div style="padding:16px;border-radius:16px;background:#f8fafc;border:1px solid #e2e8f0;">
  <p style="margin:0;color:#475569;font-size:14px;line-height:1.7;">Please log in to the Leave Management System to approve or reject this request.</p>
</div>',
       TRUE
WHERE NOT EXISTS (SELECT 1 FROM email_schema.email_template WHERE template_type = 'PENDING_APPROVAL');

INSERT INTO email_schema.email_template (template_type, subject, body_html, active)
SELECT 'MANAGER_APPROVED_PENDING',
       'Leave Approved by Manager – Awaiting Your Final Approval',
       '<p style="margin:0 0 12px;color:#475569;font-size:15px;line-height:1.7;">Hello <strong>Admin</strong>,</p>
<p style="margin:0 0 18px;color:#475569;font-size:15px;line-height:1.7;">A leave request has been <strong style="color:#0f8b8d;">approved by the manager</strong> and is now awaiting your final approval.</p>
<div style="margin:0 0 22px;padding:16px 20px;border-radius:16px;background:#ecfdf5;border:1px solid #bbf7d0;display:inline-block;">
  <span style="font-size:13px;font-weight:700;color:#0f8b8d;letter-spacing:0.08em;">✅ MANAGER APPROVED – PENDING ADMIN FINAL APPROVAL</span>
</div>
<table role="presentation" style="width:100%;border-collapse:separate;border-spacing:0 8px;margin:0 0 20px;">
  <tr>
    <td style="width:36%;padding:11px 14px;border-radius:12px 0 0 12px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Employee</td>
    <td style="padding:11px 14px;border-radius:0 12px 12px 0;background:#fff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">{{employeeName}}</td>
  </tr>
  <tr>
    <td style="padding:11px 14px;border-radius:12px 0 0 12px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Leave Type</td>
    <td style="padding:11px 14px;border-radius:0 12px 12px 0;background:#fff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">{{leaveType}}</td>
  </tr>
  <tr>
    <td style="padding:11px 14px;border-radius:12px 0 0 12px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Reason</td>
    <td style="padding:11px 14px;border-radius:0 12px 12px 0;background:#fff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">{{reason}}</td>
  </tr>
  <tr>
    <td style="padding:11px 14px;border-radius:12px 0 0 12px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Approved By Manager</td>
    <td style="padding:11px 14px;border-radius:0 12px 12px 0;background:#fff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;"><strong>{{managerName}}</strong></td>
  </tr>
</table>
{{datesTable}}
<div style="padding:16px;border-radius:16px;background:#f8fafc;border:1px solid #e2e8f0;">
  <p style="margin:0;color:#475569;font-size:14px;line-height:1.7;">Please log in to the Leave Management System to give your final approval or rejection.</p>
</div>',
       TRUE
WHERE NOT EXISTS (SELECT 1 FROM email_schema.email_template WHERE template_type = 'MANAGER_APPROVED_PENDING');

INSERT INTO email_schema.email_template (template_type, subject, body_html, active)
SELECT 'LEAVE_APPROVED',
       'Your Leave Has Been Approved ✅',
       '<p style="margin:0 0 12px;color:#475569;font-size:15px;line-height:1.7;">Hello <strong>{{employeeName}}</strong>,</p>
<p style="margin:0 0 20px;color:#475569;font-size:15px;line-height:1.7;">Great news! Your leave request has been <strong style="color:#0f8b8d;">approved</strong>.</p>
<div style="margin:0 0 22px;padding:16px 20px;border-radius:16px;background:#ecfdf5;border:1px solid #bbf7d0;display:inline-block;">
  <span style="font-size:13px;font-weight:700;color:#0f8b8d;letter-spacing:0.08em;">✅ STATUS: APPROVED</span>
</div>
<table role="presentation" style="width:100%;border-collapse:separate;border-spacing:0 8px;margin:0 0 20px;">
  <tr>
    <td style="width:36%;padding:11px 14px;border-radius:12px 0 0 12px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Employee</td>
    <td style="padding:11px 14px;border-radius:0 12px 12px 0;background:#fff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">{{employeeName}}</td>
  </tr>
  <tr>
    <td style="padding:11px 14px;border-radius:12px 0 0 12px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Leave Type</td>
    <td style="padding:11px 14px;border-radius:0 12px 12px 0;background:#fff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">{{leaveType}}</td>
  </tr>
  <tr>
    <td style="padding:11px 14px;border-radius:12px 0 0 12px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Reason</td>
    <td style="padding:11px 14px;border-radius:0 12px 12px 0;background:#fff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">{{reason}}</td>
  </tr>
  <tr>
    <td style="padding:11px 14px;border-radius:12px 0 0 12px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Approved By</td>
    <td style="padding:11px 14px;border-radius:0 12px 12px 0;background:#fff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;"><strong>{{actionBy}}</strong> <span style="color:#64748b;font-size:13px;background:#f1f5f9;padding:2px 8px;border-radius:6px;margin-left:4px;">{{actionByRole}}</span></td>
  </tr>
</table>
{{datesTable}}
<div style="padding:16px 18px;border-radius:14px;background:#f0fdfa;border:1px solid #99f6e4;">
  <p style="margin:0;color:#0f766e;font-size:14px;line-height:1.7;">Please ensure you complete any pending tasks before your leave begins. Have a great time off! 🎉</p>
</div>',
       TRUE
WHERE NOT EXISTS (SELECT 1 FROM email_schema.email_template WHERE template_type = 'LEAVE_APPROVED');

INSERT INTO email_schema.email_template (template_type, subject, body_html, active)
SELECT 'LEAVE_REJECTED',
       'Your Leave Request Has Been Rejected',
       '<p style="margin:0 0 12px;color:#475569;font-size:15px;line-height:1.7;">Hello <strong>{{employeeName}}</strong>,</p>
<p style="margin:0 0 20px;color:#475569;font-size:15px;line-height:1.7;">We regret to inform you that your leave request has been <strong style="color:#dc2626;">rejected</strong>.</p>
<div style="margin:0 0 22px;padding:16px 20px;border-radius:16px;background:#fef2f2;border:1px solid #fecaca;display:inline-block;">
  <span style="font-size:13px;font-weight:700;color:#dc2626;letter-spacing:0.08em;">❌ STATUS: REJECTED</span>
</div>
<table role="presentation" style="width:100%;border-collapse:separate;border-spacing:0 8px;margin:0 0 20px;">
  <tr>
    <td style="width:36%;padding:11px 14px;border-radius:12px 0 0 12px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Employee</td>
    <td style="padding:11px 14px;border-radius:0 12px 12px 0;background:#fff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">{{employeeName}}</td>
  </tr>
  <tr>
    <td style="padding:11px 14px;border-radius:12px 0 0 12px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Leave Type</td>
    <td style="padding:11px 14px;border-radius:0 12px 12px 0;background:#fff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">{{leaveType}}</td>
  </tr>
  <tr>
    <td style="padding:11px 14px;border-radius:12px 0 0 12px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Reason</td>
    <td style="padding:11px 14px;border-radius:0 12px 12px 0;background:#fff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">{{reason}}</td>
  </tr>
  <tr>
    <td style="padding:11px 14px;border-radius:12px 0 0 12px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Rejected By</td>
    <td style="padding:11px 14px;border-radius:0 12px 12px 0;background:#fff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;"><strong>{{actionBy}}</strong> <span style="color:#64748b;font-size:13px;background:#f1f5f9;padding:2px 8px;border-radius:6px;margin-left:4px;">{{actionByRole}}</span></td>
  </tr>
  <tr>
    <td style="padding:11px 14px;border-radius:12px 0 0 12px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Rejection Reason</td>
    <td style="padding:11px 14px;border-radius:0 12px 12px 0;background:#fff;border:1px solid #fecaca;border-left:0;color:#dc2626;font-size:14px;">{{rejectionReason}}</td>
  </tr>
</table>
{{datesTable}}
<div style="padding:16px 18px;border-radius:14px;background:#fef2f2;border:1px solid #fecaca;">
  <p style="margin:0;color:#991b1b;font-size:14px;line-height:1.7;">If you have questions about this decision, please contact your manager or HR team directly.</p>
</div>',
       TRUE
WHERE NOT EXISTS (SELECT 1 FROM email_schema.email_template WHERE template_type = 'LEAVE_REJECTED');

INSERT INTO email_schema.email_template (template_type, subject, body_html, active)
SELECT 'REMINDER_4DAY',
       'Reminder: Leave Approval Needed in 4 Days',
       '<p style="margin:0 0 12px;color:#475569;font-size:15px;line-height:1.7;">Hello <strong>{{managerName}}</strong> <span style="color:#64748b;font-size:13px;">({{managerRole}})</span>,</p>
<div style="margin:0 0 18px;padding:20px;border-radius:18px;background:linear-gradient(135deg,#ecfeff,#fff7ed);border:1px solid #dbeafe;text-align:center;">
  <div style="font-size:12px;letter-spacing:0.18em;text-transform:uppercase;color:#0f766e;font-weight:700;margin-bottom:8px;">Reminder</div>
  <div style="font-size:26px;color:#0f172a;font-weight:800;">4 days remaining</div>
  <div style="font-size:13px;color:#64748b;margin-top:6px;">Leave starts in 4 days and is still pending approval.</div>
</div>
<table role="presentation" style="width:100%;border-collapse:separate;border-spacing:0 10px;margin:0 0 18px;">
  <tr>
    <td style="width:38%;padding:12px 14px;border-radius:14px 0 0 14px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Employee</td>
    <td style="padding:12px 14px;border-radius:0 14px 14px 0;background:#ffffff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">{{employeeName}}</td>
  </tr>
  <tr>
    <td style="padding:12px 14px;border-radius:14px 0 0 14px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Employee Role</td>
    <td style="padding:12px 14px;border-radius:0 14px 14px 0;background:#ffffff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">{{employeeRole}}</td>
  </tr>
  <tr>
    <td style="padding:12px 14px;border-radius:14px 0 0 14px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Leave Type</td>
    <td style="padding:12px 14px;border-radius:0 14px 14px 0;background:#ffffff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">{{leaveType}}</td>
  </tr>
  <tr>
    <td style="padding:12px 14px;border-radius:14px 0 0 14px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Reason</td>
    <td style="padding:12px 14px;border-radius:0 14px 14px 0;background:#ffffff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">{{reason}}</td>
  </tr>
</table>
{{datesTable}}
<div style="padding:16px;border-radius:16px;background:#f8fafc;border:1px solid #e2e8f0;">
  <p style="margin:0;color:#475569;font-size:14px;line-height:1.7;">Please log in to the Leave Management System to take action before the leave begins.</p>
</div>',
       TRUE
WHERE NOT EXISTS (SELECT 1 FROM email_schema.email_template WHERE template_type = 'REMINDER_4DAY');

INSERT INTO email_schema.email_template (template_type, subject, body_html, active)
SELECT 'REMINDER_2DAY',
       'Reminder: Leave Approval Needed in 2 Days',
       '<p style="margin:0 0 12px;color:#475569;font-size:15px;line-height:1.7;">Hello <strong>{{managerName}}</strong> <span style="color:#64748b;font-size:13px;">({{managerRole}})</span>,</p>
<div style="margin:0 0 18px;padding:20px;border-radius:18px;background:linear-gradient(135deg,#ecfeff,#fff7ed);border:1px solid #dbeafe;text-align:center;">
  <div style="font-size:12px;letter-spacing:0.18em;text-transform:uppercase;color:#0f766e;font-weight:700;margin-bottom:8px;">Reminder</div>
  <div style="font-size:26px;color:#0f172a;font-weight:800;">2 days remaining</div>
  <div style="font-size:13px;color:#64748b;margin-top:6px;">Leave starts in 2 days and is still pending approval.</div>
</div>
<table role="presentation" style="width:100%;border-collapse:separate;border-spacing:0 10px;margin:0 0 18px;">
  <tr>
    <td style="width:38%;padding:12px 14px;border-radius:14px 0 0 14px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Employee</td>
    <td style="padding:12px 14px;border-radius:0 14px 14px 0;background:#ffffff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">{{employeeName}}</td>
  </tr>
  <tr>
    <td style="padding:12px 14px;border-radius:14px 0 0 14px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Employee Role</td>
    <td style="padding:12px 14px;border-radius:0 14px 14px 0;background:#ffffff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">{{employeeRole}}</td>
  </tr>
  <tr>
    <td style="padding:12px 14px;border-radius:14px 0 0 14px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Leave Type</td>
    <td style="padding:12px 14px;border-radius:0 14px 14px 0;background:#ffffff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">{{leaveType}}</td>
  </tr>
  <tr>
    <td style="padding:12px 14px;border-radius:14px 0 0 14px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Reason</td>
    <td style="padding:12px 14px;border-radius:0 14px 14px 0;background:#ffffff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">{{reason}}</td>
  </tr>
</table>
{{datesTable}}
<div style="padding:16px;border-radius:16px;background:#f8fafc;border:1px solid #e2e8f0;">
  <p style="margin:0;color:#475569;font-size:14px;line-height:1.7;">Please log in to the Leave Management System to take action before the leave begins.</p>
</div>',
       TRUE
WHERE NOT EXISTS (SELECT 1 FROM email_schema.email_template WHERE template_type = 'REMINDER_2DAY');

-- ============================================================
-- SEED: user-service email templates
-- ============================================================
INSERT INTO email_schema.email_template (template_type, subject, body_html, active)
SELECT 'PASSWORD_RESET_OTP',
       'Your Password Reset OTP',
       '<p style="margin:0 0 12px;color:#475569;font-size:15px;line-height:1.7;">Hello <strong>{{fullName}}</strong>,</p>
<p style="margin:0 0 16px;color:#475569;font-size:15px;line-height:1.7;">Use the OTP below to reset your password.</p>
<div style="margin:0 0 20px;padding:24px;border-radius:18px;background:linear-gradient(135deg,#ecfeff,#fff7ed);border:1px solid #dbeafe;text-align:center;">
  <div style="font-size:12px;letter-spacing:0.18em;text-transform:uppercase;color:#0f766e;font-weight:700;margin-bottom:10px;">Password Reset OTP</div>
  <div style="font-size:34px;letter-spacing:0.32em;color:#0f172a;font-weight:800;">{{otp}}</div>
</div>
<div style="padding:16px;border-radius:14px;background:#f8fafc;border:1px solid #e2e8f0;">
  <p style="margin:0;color:#475569;font-size:14px;line-height:1.7;">This OTP expires in <strong>{{otpExpiry}} minutes</strong>. If you did not request a password reset, you can safely ignore this email.</p>
</div>',
       TRUE
WHERE NOT EXISTS (SELECT 1 FROM email_schema.email_template WHERE template_type = 'PASSWORD_RESET_OTP');

INSERT INTO email_schema.email_template (template_type, subject, body_html, active)
SELECT 'USER_CREATED',
       'Your Cresen Solutions Account Is Ready',
       '<p style="margin:0 0 12px;color:#475569;font-size:15px;line-height:1.7;">Hello <strong>{{fullName}}</strong>,</p>
<p style="margin:0 0 18px;color:#475569;font-size:15px;line-height:1.7;">Your Cresen Solutions account has been created successfully. For security, we do not send passwords by email.</p>
<table role="presentation" style="width:100%;border-collapse:separate;border-spacing:0 8px;margin:0 0 20px;">
  <tr>
    <td style="width:36%;padding:11px 14px;border-radius:12px 0 0 12px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">User ID</td>
    <td style="padding:11px 14px;border-radius:0 12px 12px 0;background:#fff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">{{userId}}</td>
  </tr>
  <tr>
    <td style="padding:11px 14px;border-radius:12px 0 0 12px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Company ID</td>
    <td style="padding:11px 14px;border-radius:0 12px 12px 0;background:#fff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">{{companyId}}</td>
  </tr>
  <tr>
    <td style="padding:11px 14px;border-radius:12px 0 0 12px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Username</td>
    <td style="padding:11px 14px;border-radius:0 12px 12px 0;background:#fff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">{{username}}</td>
  </tr>
  <tr>
    <td style="padding:11px 14px;border-radius:12px 0 0 12px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Role</td>
    <td style="padding:11px 14px;border-radius:0 12px 12px 0;background:#fff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">{{role}}</td>
  </tr>
</table>
<div style="margin:0 0 18px;padding:18px;border-radius:16px;background:linear-gradient(135deg,#ecfeff,#fff7ed);border:1px solid #dbeafe;">
  <p style="margin:0 0 10px;color:#0f172a;font-size:15px;font-weight:700;">Set your password securely</p>
  <p style="margin:0 0 14px;color:#475569;font-size:14px;line-height:1.7;">Use the button below to create your password before signing in.</p>
  <a href="{{resetLink}}" style="display:inline-block;padding:12px 20px;border-radius:12px;background:#0f8b8d;color:#ffffff;text-decoration:none;font-size:14px;font-weight:700;">Set Password</a>
</div>
<p style="margin:0;color:#64748b;font-size:13px;line-height:1.7;">If the button does not work, copy and open this link:<br><a href="{{resetLink}}" style="color:#0f766e;text-decoration:none;">{{resetLink}}</a></p>',
       TRUE
WHERE NOT EXISTS (SELECT 1 FROM email_schema.email_template WHERE template_type = 'USER_CREATED');

INSERT INTO email_schema.email_template (template_type, subject, body_html, active)
SELECT 'USER_DELETED',
       'Your Cresen Solutions Account Has Been Removed',
       '<p style="margin:0 0 12px;color:#475569;font-size:15px;line-height:1.7;">Hello <strong>{{fullName}}</strong>,</p>
<p style="margin:0 0 18px;color:#475569;font-size:15px;line-height:1.7;">Your Cresen Solutions account has been deleted and you no longer have access to the Leave Management System.</p>
<table role="presentation" style="width:100%;border-collapse:separate;border-spacing:0 8px;margin:0 0 20px;">
  <tr>
    <td style="width:36%;padding:11px 14px;border-radius:12px 0 0 12px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Username</td>
    <td style="padding:11px 14px;border-radius:0 12px 12px 0;background:#fff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">{{username}}</td>
  </tr>
  <tr>
    <td style="padding:11px 14px;border-radius:12px 0 0 12px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Role</td>
    <td style="padding:11px 14px;border-radius:0 12px 12px 0;background:#fff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">{{role}}</td>
  </tr>
  <tr>
    <td style="padding:11px 14px;border-radius:12px 0 0 12px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Deleted By</td>
    <td style="padding:11px 14px;border-radius:0 12px 12px 0;background:#fff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">{{deletedBy}} ({{deletedByRole}})</td>
  </tr>
</table>
<div style="padding:16px 18px;border-radius:14px;background:#fef2f2;border:1px solid #fecaca;">
  <p style="margin:0;color:#991b1b;font-size:14px;line-height:1.7;">If you believe this was a mistake, please contact your administrator or HR team.</p>
</div>',
       TRUE
WHERE NOT EXISTS (SELECT 1 FROM email_schema.email_template WHERE template_type = 'USER_DELETED');

INSERT INTO email_schema.email_template (template_type, subject, body_html, active)
SELECT 'USER_ROLE_CHANGED',
       'Your Role Has Been Updated',
       '<p style="margin:0 0 12px;color:#475569;font-size:15px;line-height:1.7;">Hello <strong>{{fullName}}</strong>,</p>
<p style="margin:0 0 18px;color:#475569;font-size:15px;line-height:1.7;">Your role in the Cresen Solutions Leave Management System has been updated. Please sign in again to access your new dashboard.</p>
<table role="presentation" style="width:100%;border-collapse:separate;border-spacing:0 8px;margin:0 0 20px;">
  <tr>
    <td style="width:36%;padding:11px 14px;border-radius:12px 0 0 12px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Username</td>
    <td style="padding:11px 14px;border-radius:0 12px 12px 0;background:#fff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">{{username}}</td>
  </tr>
  <tr>
    <td style="padding:11px 14px;border-radius:12px 0 0 12px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Previous Role</td>
    <td style="padding:11px 14px;border-radius:0 12px 12px 0;background:#fff;border:1px solid #e2e8f0;border-left:0;color:#64748b;font-size:14px;">{{previousRole}}</td>
  </tr>
  <tr>
    <td style="padding:11px 14px;border-radius:12px 0 0 12px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">New Role</td>
    <td style="padding:11px 14px;border-radius:0 12px 12px 0;background:#fff;border:1px solid #e2e8f0;border-left:0;color:#0f8b8d;font-size:14px;font-weight:700;">{{newRole}}</td>
  </tr>
  <tr>
    <td style="padding:11px 14px;border-radius:12px 0 0 12px;background:#f8fafc;color:#64748b;font-size:13px;font-weight:700;">Changed By</td>
    <td style="padding:11px 14px;border-radius:0 12px 12px 0;background:#fff;border:1px solid #e2e8f0;border-left:0;color:#0f172a;font-size:14px;">{{changedBy}} ({{changedByRole}})</td>
  </tr>
</table>
<div style="margin:0 0 18px;padding:18px;border-radius:16px;background:linear-gradient(135deg,#ecfeff,#fff7ed);border:1px solid #dbeafe;">
  <p style="margin:0 0 10px;color:#0f172a;font-size:15px;font-weight:700;">Open your updated dashboard</p>
  <p style="margin:0 0 14px;color:#475569;font-size:14px;line-height:1.7;">Sign in to review your new permissions and workspace.</p>
  <a href="{{loginUrl}}" style="display:inline-block;padding:12px 20px;border-radius:12px;background:#0f8b8d;color:#ffffff;text-decoration:none;font-size:14px;font-weight:700;">Login To Dashboard</a>
</div>
<p style="margin:0;color:#64748b;font-size:13px;line-height:1.7;">If the button does not work, copy and open this link:<br><a href="{{loginUrl}}" style="color:#0f766e;text-decoration:none;">{{loginUrl}}</a></p>',
       TRUE
WHERE NOT EXISTS (SELECT 1 FROM email_schema.email_template WHERE template_type = 'USER_ROLE_CHANGED');
