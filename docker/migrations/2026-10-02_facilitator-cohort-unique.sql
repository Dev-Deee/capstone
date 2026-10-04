-- FacilitatorCohort is unique per (facilitator, cohort, SUBJECT), but databases
-- created before subject was added to that key still carry the old unique
-- constraint on (facilitator_id, cohort_id). ddl-auto=update adds new
-- constraints but never drops old ones, so assigning a facilitator a second
-- subject in the same cohort failed with a 500. Run on every existing database:
--   docker exec -i cadence_db psql -U postgres -d cadence_db < docker/migrations/2026-10-02_facilitator-cohort-unique.sql
-- Safe to re-run.

DO $$
DECLARE c record;
BEGIN
    -- Drop any unique constraint whose columns are exactly (facilitator_id, cohort_id).
    FOR c IN
        SELECT con.conname
        FROM pg_constraint con
        WHERE con.conrelid = 'facilitator_cohort'::regclass
          AND con.contype = 'u'
          AND (SELECT array_agg(att.attname::text ORDER BY att.attname)
               FROM unnest(con.conkey) k JOIN pg_attribute att
                 ON att.attrelid = con.conrelid AND att.attnum = k)
              = ARRAY['cohort_id', 'facilitator_id']
    LOOP
        EXECUTE format('ALTER TABLE facilitator_cohort DROP CONSTRAINT %I', c.conname);
        RAISE NOTICE 'Dropped stale constraint %', c.conname;
    END LOOP;
END $$;
