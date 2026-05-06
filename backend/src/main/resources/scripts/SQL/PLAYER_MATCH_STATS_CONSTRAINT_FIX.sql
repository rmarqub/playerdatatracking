-- ──────────────────────────────────────────────────────────────────────────
-- PLAYER_MATCH_STATS_CONSTRAINT_FIX.sql
--
-- The ON CONFLICT clause in PlayerJsonIngestService uses 4 columns:
-- (player_id, league_id, season, match_bucket).
-- PostgreSQL requires an exact-match unique constraint for that to work.
-- If the table was originally created without 'season' in the constraint,
-- every INSERT would fail silently (error caught in Java catch block).
--
-- Run this script ONCE to fix the constraint.
-- ──────────────────────────────────────────────────────────────────────────

-- Step 1: Identify and drop any existing unique constraint that does NOT
-- include season.  Run '\d player_match_stats' in psql first to see the
-- actual constraint name, then replace the placeholder below.

-- Example (replace constraint name as needed):
-- ALTER TABLE player_match_stats
--     DROP CONSTRAINT IF EXISTS player_match_stats_player_id_league_id_match_bucket_key;

-- Or drop by index name:
-- DROP INDEX IF EXISTS player_match_stats_uq;

-- Step 2: Add the correct 4-column unique constraint.
-- If you hit "already exists", drop the old one first (Step 1).
ALTER TABLE player_match_stats
    ADD CONSTRAINT player_match_stats_uq
    UNIQUE (player_id, league_id, season, match_bucket);
