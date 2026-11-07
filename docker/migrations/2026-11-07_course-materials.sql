-- Course materials. ddl-auto=update creates the course, course_section,
-- course_material and course_cohort tables on its own, but never rewrites
-- CHECK constraints on existing @Enumerated(STRING) columns, so the new admin
-- permission and notification type need this on every existing database:
--   docker exec -i cadence_db psql -U postgres -d cadence_db < docker/migrations/2026-11-07_course-materials.sql
-- Run after 2026-11-07_review-and-grading.sql. Safe to re-run.

BEGIN;

ALTER TABLE admin_role_permission DROP CONSTRAINT IF EXISTS admin_role_permission_permission_check;
ALTER TABLE admin_role_permission ADD CONSTRAINT admin_role_permission_permission_check
    CHECK (permission IN (
        'MANAGE_COHORTS', 'MANAGE_SUBJECTS', 'MANAGE_FACILITATOR_ASSIGNMENTS', 'MANAGE_INVITE_CODES',
        'MANAGE_RESUMPTION_CODES', 'VIEW_FEEDBACK', 'MANAGE_USERS', 'VIEW_INSTITUTION_ANALYTICS',
        'MANAGE_ASSIGNMENTS', 'MANAGE_ENROLLMENTS', 'MANAGE_COURSES'
    ));

ALTER TABLE notification DROP CONSTRAINT IF EXISTS notification_type_check;
ALTER TABLE notification ADD CONSTRAINT notification_type_check
    CHECK (type IN ('ATTENDANCE_OVERRIDDEN', 'COHORT_CHANGED', 'INVITE_ACCEPTED', 'SUBMISSION_SENT_BACK',
                    'GRADES_RELEASED', 'COURSE_PUBLISHED'));

COMMIT;
