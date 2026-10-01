-- spring.jpa.hibernate.ddl-auto=update adds new columns but never rewrites the
-- CHECK constraints Hibernate generated for existing @Enumerated(STRING) columns,
-- so enum changes need this applied by hand to every existing database:
--   docker exec -i cadence_db psql -U postgres -d cadence_db < docker/migrations/2026-10-01_closed-by-and-attendance-status.sql
-- Safe to re-run.

BEGIN;

-- SessionClosedBy: MANAGEMENT removed, ADMIN / SUPER_ADMIN added.
-- Existing MANAGEMENT rows came from the admin-gated resumption close endpoint;
-- the actual closer's role wasn't recorded, so they map to ADMIN.
ALTER TABLE resumption_session DROP CONSTRAINT IF EXISTS resumption_session_closed_by_check;
UPDATE resumption_session SET closed_by = 'ADMIN' WHERE closed_by = 'MANAGEMENT';
ALTER TABLE resumption_session ADD CONSTRAINT resumption_session_closed_by_check
    CHECK (closed_by IN ('FACILITATOR', 'ADMIN', 'SUPER_ADMIN', 'SYSTEM_EXPIRY'));

ALTER TABLE session DROP CONSTRAINT IF EXISTS session_closed_by_check;
ALTER TABLE session ADD CONSTRAINT session_closed_by_check
    CHECK (closed_by IN ('FACILITATOR', 'ADMIN', 'SUPER_ADMIN', 'SYSTEM_EXPIRY'));

-- AttendanceStatus: MANUAL_OVERRIDE moved to VerificationMethod; LATE / ABSENT added.
ALTER TABLE attendance_record DROP CONSTRAINT IF EXISTS attendance_record_status_check;
UPDATE attendance_record SET status = 'PRESENT', verification_method = 'MANUAL_OVERRIDE'
    WHERE status = 'MANUAL_OVERRIDE';
ALTER TABLE attendance_record ADD CONSTRAINT attendance_record_status_check
    CHECK (status IN ('PRESENT', 'LATE', 'ABSENT'));

ALTER TABLE attendance_record DROP CONSTRAINT IF EXISTS attendance_record_verification_method_check;
ALTER TABLE attendance_record ADD CONSTRAINT attendance_record_verification_method_check
    CHECK (verification_method IN ('CODE', 'MANUAL_OVERRIDE'));

COMMIT;
