-- Canonical v2 baseline: 10 business tables. Use Flyway for application databases.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash TEXT NOT NULL
);

CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    deadline TIMESTAMPTZ
);

CREATE TABLE project_members (
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    PRIMARY KEY (project_id, user_id),
    CONSTRAINT chk_member_role CHECK (role IN ('LEAD', 'MEMBER'))
);

CREATE TABLE IF NOT EXISTS tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    assignee_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    expected_output TEXT,
    start_date DATE,
    deadline TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'TODO'
        CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE')),
    planning_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (planning_status IN ('DRAFT', 'CONFIRMED')),
    confirmed_by UUID REFERENCES users(id),
    confirmed_at TIMESTAMPTZ,
    CONSTRAINT fk_task_assignee_member
        FOREIGN KEY (project_id, assignee_id)
        REFERENCES project_members(project_id, user_id),
    CONSTRAINT chk_task_plan_confirmation CHECK (
        (planning_status = 'DRAFT' AND confirmed_by IS NULL AND confirmed_at IS NULL)
        OR
        (planning_status = 'CONFIRMED' AND confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL)
    ),
    CONSTRAINT chk_task_dates CHECK (
        start_date IS NULL OR deadline >= (start_date::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh')
    )
);

-- 5. Weekly task reports: one report per task per Friday checkpoint.
-- checkpoint_date = Friday date in the PROJECT'S operating timezone (Asia/Bangkok for the MVP).
CREATE TABLE IF NOT EXISTS task_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    checkpoint_date DATE NOT NULL,
    result VARCHAR(20) NOT NULL
        CHECK (result IN ('ACHIEVED', 'NOT_ACHIEVED')),
    note TEXT,
    evidence_url TEXT,
    reported_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (task_id, checkpoint_date),
    CONSTRAINT chk_checkpoint_friday CHECK (EXTRACT(ISODOW FROM checkpoint_date) = 5)
);

-- 6. Need Help: member -> TL (Solution 1)
CREATE TABLE IF NOT EXISTS support_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    requested_by UUID NOT NULL REFERENCES users(id),
    reason TEXT NOT NULL,
    requested_help TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'RESOLVED')),
    leader_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ
);

-- 7. In-app notifications (created by backend/scheduler)
CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    type VARCHAR(30) NOT NULL
        CHECK (type IN ('CHECKPOINT_REMINDER', 'CHECKPOINT_ALERT', 'NEED_HELP', 'HELP_UPDATE')),
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    checkpoint_date DATE,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 8. Voluntary help requests (Solution 2)
CREATE TABLE IF NOT EXISTS help_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    created_by UUID NOT NULL REFERENCES users(id),
    title VARCHAR(200) NOT NULL,
    description TEXT,
    deadline TIMESTAMPTZ,
    estimated_minutes INTEGER CHECK (estimated_minutes IS NULL OR estimated_minutes >= 0),
    volunteer_id UUID REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'CLAIMED', 'SUBMITTED', 'COMPLETED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_help_volunteer CHECK (
        (status = 'OPEN' AND volunteer_id IS NULL)
        OR status = 'CANCELLED'
        OR (status IN ('CLAIMED','SUBMITTED','COMPLETED') AND volunteer_id IS NOT NULL)
    )
);

-- 9. Submitted contribution and TL verification
CREATE TABLE IF NOT EXISTS contributions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    help_request_id UUID NOT NULL UNIQUE REFERENCES help_requests(id) ON DELETE CASCADE,
    result_note TEXT,
    evidence_url TEXT,
    actual_minutes INTEGER CHECK (actual_minutes IS NULL OR actual_minutes >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    verified_by UUID REFERENCES users(id),
    verified_at TIMESTAMPTZ
);

-- 10. Compensation agreement (one per verified contribution)
CREATE TABLE IF NOT EXISTS compensations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contribution_id UUID NOT NULL UNIQUE REFERENCES contributions(id) ON DELETE CASCADE,
    description TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PROPOSED'
        CHECK (status IN ('PROPOSED', 'AGREED', 'FULFILLED')),
    agreed_at TIMESTAMPTZ,
    fulfilled_at TIMESTAMPTZ
);

-- Small indexes for common dashboard lookups
CREATE INDEX IF NOT EXISTS idx_tasks_project_assignee ON tasks(project_id, assignee_id);
CREATE INDEX IF NOT EXISTS idx_tasks_deadline ON tasks(deadline);
CREATE INDEX IF NOT EXISTS idx_reports_checkpoint ON task_reports(checkpoint_date);
CREATE INDEX IF NOT EXISTS idx_support_task_status ON support_requests(task_id, status);
CREATE INDEX IF NOT EXISTS idx_notifications_recipient ON notifications(recipient_id, read_at);
CREATE INDEX IF NOT EXISTS idx_help_task_status ON help_requests(task_id, status);


-- Custom sessions are authenticated by Spring, not by Supabase Auth JWTs.
-- The backend connects as the trusted PostgreSQL owner. Direct anon/authenticated
-- PostgREST clients must not bypass project authorization or read password hashes.
-- With no client policies, RLS denies direct client access while the owner can
-- continue using the tables. No data is modified; Solution 2 tables are unchanged.
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE projects ENABLE ROW LEVEL SECURITY;
ALTER TABLE project_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE tasks ENABLE ROW LEVEL SECURITY;
ALTER TABLE task_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE support_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;
