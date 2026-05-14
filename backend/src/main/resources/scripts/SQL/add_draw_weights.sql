-- Adds draw-axis weight columns to contextual_weight_config
-- Run once against the target database before restarting the backend.

ALTER TABLE contextual_weight_config
  ADD COLUMN IF NOT EXISTS w_forma_d      DOUBLE PRECISION DEFAULT 0,
  ADD COLUMN IF NOT EXISTS w_needs_d      DOUBLE PRECISION DEFAULT 0,
  ADD COLUMN IF NOT EXISTS w_def_d        DOUBLE PRECISION DEFAULT 0,
  ADD COLUMN IF NOT EXISTS w_off_d        DOUBLE PRECISION DEFAULT 0,
  ADD COLUMN IF NOT EXISTS w_fatigue_d    DOUBLE PRECISION DEFAULT 0,
  ADD COLUMN IF NOT EXISTS w_set_pieces_d DOUBLE PRECISION DEFAULT 0,
  ADD COLUMN IF NOT EXISTS w_atm_d        DOUBLE PRECISION DEFAULT 0,
  ADD COLUMN IF NOT EXISTS w_unavail_d    DOUBLE PRECISION DEFAULT 0;
