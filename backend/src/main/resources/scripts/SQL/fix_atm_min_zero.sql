-- Ensures w_atm and w_atm_d are never negative in contextual_weight_config.
-- Step 1: clamp existing negative values to 0
UPDATE contextual_weight_config
SET
  w_atm   = GREATEST(w_atm,   0),
  w_atm_d = GREATEST(w_atm_d, 0)
WHERE w_atm < 0 OR w_atm_d < 0;

-- Step 2: add CHECK constraints so future rows can't go below 0
ALTER TABLE contextual_weight_config
  ADD CONSTRAINT IF NOT EXISTS chk_w_atm_min_zero   CHECK (w_atm   >= 0),
  ADD CONSTRAINT IF NOT EXISTS chk_w_atm_d_min_zero CHECK (w_atm_d >= 0);
