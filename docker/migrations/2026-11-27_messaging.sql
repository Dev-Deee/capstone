-- In-app messaging: direct conversations and cohort announcements.
-- ddl-auto=update creates the conversation, message, announcement and
-- announcement_cohort tables on its own, but never rewrites CHECK constraints
-- on existing @Enumerated(STRING) columns, so the two new notification types
-- need this on every existing database:
--   docker exec -i cadence_db psql -U postgres -d cadence_db < docker/migrations/2026-11-27_messaging.sql
-- Run after 2026-11-07_course-materials.sql. Safe to re-run.

BEGIN;

ALTER TABLE notification DROP CONSTRAINT IF EXISTS notification_type_check;
ALTER TABLE notification ADD CONSTRAINT notification_type_check
    CHECK (type IN ('ATTENDANCE_OVERRIDDEN', 'COHORT_CHANGED', 'INVITE_ACCEPTED', 'SUBMISSION_SENT_BACK',
                    'GRADES_RELEASED', 'COURSE_PUBLISHED', 'NEW_MESSAGE', 'ANNOUNCEMENT_POSTED'));

COMMIT;
