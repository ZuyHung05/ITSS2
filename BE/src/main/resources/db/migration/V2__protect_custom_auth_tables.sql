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
