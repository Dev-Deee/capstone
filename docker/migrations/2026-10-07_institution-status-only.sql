-- Institution.status becomes the only source of truth for whether an
-- institution is usable (ACTIVE only); the is_active flag is removed.
--   docker exec -i cadence_db psql -U postgres -d cadence_db < docker/migrations/2026-10-07_institution-status-only.sql
-- Safe to re-run. Apply together with the code that removes Institution.isActive:
-- is_active is NOT NULL without a default, so new code creating institutions
-- against an un-migrated database fails, and old code reading a migrated one
-- fails too.

BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'institution' AND column_name = 'is_active') THEN
        -- Backfill first, so no switched-off institution silently becomes usable:
        -- inactive but still marked ACTIVE → SUSPENDED. PENDING / SUSPENDED stay as they are.
        UPDATE institution SET status = 'SUSPENDED' WHERE is_active = false AND status = 'ACTIVE';
        ALTER TABLE institution DROP COLUMN is_active;
    END IF;
END $$;

COMMIT;
