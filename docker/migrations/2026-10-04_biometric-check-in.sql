-- PRD Section 6.12 — biometric confirmation at check-in.
-- ddl-auto=update adds attendance_record.web_authn_verified on its own, but
-- never rewrites the CHECK constraints on existing @Enumerated(STRING)
-- columns, so the new PENDING_BIOMETRIC status needs this on every existing
-- database:
--   docker exec -i cadence_db psql -U postgres -d cadence_db < docker/migrations/2026-10-04_biometric-check-in.sql
-- Safe to re-run.

BEGIN;

ALTER TABLE attendance_record ADD COLUMN IF NOT EXISTS web_authn_verified BOOLEAN;

ALTER TABLE attendance_record DROP CONSTRAINT IF EXISTS attendance_record_status_check;
ALTER TABLE attendance_record ADD CONSTRAINT attendance_record_status_check
    CHECK (status IN ('PRESENT', 'LATE', 'ABSENT', 'PENDING_BIOMETRIC'));

ALTER TABLE resumption_attendance_record DROP CONSTRAINT IF EXISTS resumption_attendance_record_status_check;
ALTER TABLE resumption_attendance_record ADD CONSTRAINT resumption_attendance_record_status_check
    CHECK (status IN ('PRESENT', 'LATE', 'ABSENT', 'PENDING_BIOMETRIC'));

COMMIT;
