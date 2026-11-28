-- Automatic nudges. ddl-auto=update creates the nudge_log table (and its
-- unique constraint) on its own, but never rewrites CHECK constraints on
-- existing @Enumerated(STRING) columns, so the new notification types need
-- this on every existing database:
--   docker exec -i cadence_db psql -U postgres -d cadence_db < docker/migrations/2026-11-27_automatic-nudges.sql
-- Run after 2026-11-10_institution-handle.sql. Safe to re-run.

BEGIN;

ALTER TABLE notification DROP CONSTRAINT IF EXISTS notification_type_check;
ALTER TABLE notification ADD CONSTRAINT notification_type_check
    CHECK (type IN ('ATTENDANCE_OVERRIDDEN', 'COHORT_CHANGED', 'INVITE_ACCEPTED', 'SUBMISSION_SENT_BACK',
                    'GRADES_RELEASED', 'COURSE_PUBLISHED', 'ASSIGNMENT_DUE_SOON', 'ASSIGNMENT_OVERDUE',
                    'REDO_DUE_SOON', 'REVIEW_WAITING_DIGEST'));

COMMIT;
