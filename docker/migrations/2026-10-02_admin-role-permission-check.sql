-- Permission gained MANAGE_ASSIGNMENTS and MANAGE_ENROLLMENTS after the
-- admin_role_permission table was created. ddl-auto=update never rewrites the
-- CHECK constraint Hibernate generated for the @Enumerated column, so saving an
-- admin role with either permission failed (500). Run on every existing database:
--   docker exec -i cadence_db psql -U postgres -d cadence_db < docker/migrations/2026-10-02_admin-role-permission-check.sql
-- Safe to re-run.

BEGIN;

ALTER TABLE admin_role_permission DROP CONSTRAINT IF EXISTS admin_role_permission_permission_check;
ALTER TABLE admin_role_permission ADD CONSTRAINT admin_role_permission_permission_check
    CHECK (permission IN (
        'MANAGE_COHORTS', 'MANAGE_SUBJECTS', 'MANAGE_FACILITATOR_ASSIGNMENTS', 'MANAGE_INVITE_CODES',
        'MANAGE_RESUMPTION_CODES', 'VIEW_FEEDBACK', 'MANAGE_USERS', 'VIEW_INSTITUTION_ANALYTICS',
        'MANAGE_ASSIGNMENTS', 'MANAGE_ENROLLMENTS'
    ));

COMMIT;
