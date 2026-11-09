-- Institution.slug is now Institution.handle. ddl-auto=update would add an
-- empty handle column instead of renaming, so rename the column (values stay
-- exactly as they are) and its unique constraint before the new backend starts:
--   docker exec -i cadence_db psql -U postgres -d cadence_db < docker/migrations/2026-11-10_institution-handle.sql
-- The old backend reads slug, so deploy straight after running this. Safe to re-run.

BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema() AND table_name = 'institution' AND column_name = 'slug') THEN
        -- A new backend that started before this ran may have added an empty handle column.
        IF EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = current_schema() AND table_name = 'institution' AND column_name = 'handle') THEN
            IF EXISTS (SELECT 1 FROM institution WHERE handle IS NOT NULL) THEN
                RAISE EXCEPTION 'institution has both slug and a filled-in handle column; resolve by hand';
            END IF;
            ALTER TABLE institution DROP COLUMN handle;
        END IF;
        ALTER TABLE institution RENAME COLUMN slug TO handle;
    END IF;
END $$;

-- The unique constraint on the column: institution_slug_key where the
-- multitenant migration made it, a generated uk… name where Hibernate did.
DO $$
DECLARE
    c text;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conrelid = 'institution'::regclass AND conname = 'institution_handle_key') THEN
        SELECT con.conname INTO c
        FROM pg_constraint con
        JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = con.conkey[1]
        WHERE con.conrelid = 'institution'::regclass AND con.contype = 'u'
          AND array_length(con.conkey, 1) = 1 AND att.attname = 'handle'
        LIMIT 1;
        IF c IS NOT NULL THEN
            EXECUTE format('ALTER TABLE institution RENAME CONSTRAINT %I TO institution_handle_key', c);
        ELSE
            ALTER TABLE institution ADD CONSTRAINT institution_handle_key UNIQUE (handle);
        END IF;
    END IF;
END $$;

ALTER TABLE institution ALTER COLUMN handle SET NOT NULL;

COMMIT;
