-- Phase 2 — reviewing and grading submitted work.
-- ddl-auto=update adds the new columns and the submission_attempt table, but
-- never rewrites CHECK constraints on existing @Enumerated(STRING) columns, so
-- the new submission status and notification types need this on every
-- existing database:
--   docker exec -i cadence_db psql -U postgres -d cadence_db < docker/migrations/2026-11-07_review-and-grading.sql
-- Safe to re-run.

BEGIN;

ALTER TABLE assignment ADD COLUMN IF NOT EXISTS grading_type VARCHAR(20) NOT NULL DEFAULT 'MARKS';
ALTER TABLE assignment ADD COLUMN IF NOT EXISTS max_marks INTEGER;
ALTER TABLE assignment ADD COLUMN IF NOT EXISTS grades_released_at TIMESTAMP;
-- Existing assignments keep the default: marks out of 100.
UPDATE assignment SET max_marks = 100 WHERE grading_type = 'MARKS' AND max_marks IS NULL;

ALTER TABLE submission ADD COLUMN IF NOT EXISTS extra_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE submission ADD COLUMN IF NOT EXISTS score NUMERIC(7, 2);
ALTER TABLE submission ADD COLUMN IF NOT EXISTS passed BOOLEAN;
ALTER TABLE submission ADD COLUMN IF NOT EXISTS feedback TEXT;
ALTER TABLE submission ADD COLUMN IF NOT EXISTS reviewed_by_id BIGINT REFERENCES app_user (id);
ALTER TABLE submission ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP;
ALTER TABLE submission ADD COLUMN IF NOT EXISTS redo_due_at TIMESTAMP;

ALTER TABLE submission DROP CONSTRAINT IF EXISTS submission_status_check;
ALTER TABLE submission ADD CONSTRAINT submission_status_check
    CHECK (status IN ('PENDING', 'SUBMITTED', 'LATE_SUBMITTED', 'MISSING', 'REVIEWED', 'REDO_REQUESTED'));

ALTER TABLE notification DROP CONSTRAINT IF EXISTS notification_type_check;
ALTER TABLE notification ADD CONSTRAINT notification_type_check
    CHECK (type IN ('ATTENDANCE_OVERRIDDEN', 'COHORT_CHANGED', 'INVITE_ACCEPTED', 'SUBMISSION_SENT_BACK', 'GRADES_RELEASED'));

COMMIT;
