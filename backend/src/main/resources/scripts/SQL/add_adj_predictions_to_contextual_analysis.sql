-- Stores the adjusted predictions at the time the contextual analysis is saved,
-- so the history view shows what was actually predicted rather than recalculating
-- with the current (possibly updated) weights.
ALTER TABLE fixture_contextual_analysis
  ADD COLUMN IF NOT EXISTS adj_home_win FLOAT,
  ADD COLUMN IF NOT EXISTS adj_draw     FLOAT,
  ADD COLUMN IF NOT EXISTS adj_away_win FLOAT;
