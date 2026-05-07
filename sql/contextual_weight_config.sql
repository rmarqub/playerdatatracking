-- Phase 7b: contextual blend weights table
-- Run once against the playerdatatracking database.

CREATE TABLE IF NOT EXISTS contextual_weight_config (
    id               SERIAL PRIMARY KEY,
    w_forma          FLOAT NOT NULL DEFAULT 0.12,
    w_needs          FLOAT NOT NULL DEFAULT 0.10,
    w_def            FLOAT NOT NULL DEFAULT 0.07,
    w_off            FLOAT NOT NULL DEFAULT 0.07,
    w_fatigue        FLOAT NOT NULL DEFAULT 0.06,
    w_set_pieces     FLOAT NOT NULL DEFAULT 0.06,
    w_atm            FLOAT NOT NULL DEFAULT 0.03,
    calibration_date DATE,
    n_samples        INTEGER,
    notes            TEXT
);

-- Seed with Phase 7a hardcoded weights so the table is never empty.
INSERT INTO contextual_weight_config
    (w_forma, w_needs, w_def, w_off, w_fatigue, w_set_pieces, w_atm,
     calibration_date, n_samples, notes)
VALUES
    (0.12, 0.10, 0.07, 0.07, 0.06, 0.06, 0.03,
     CURRENT_DATE, 0,
     'Phase 7a hardcoded weights — replace after calibrate_weights.py collects 30+ samples');
