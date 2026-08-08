#!/bin/bash
set -e
# Reserved for future seed data (Section 4/5) — no entities exist yet, nothing to seed.
# When real seed data is needed (e.g. the Semicolon Institution row for local dev),
# connect to "$POSTGRES_DB" directly — it already exists by the time this script
# runs, thanks to POSTGRES_DB in docker-compose.yml. Do not attempt to CREATE
# DATABASE here.