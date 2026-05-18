"""
Phase 7b – Calibrate contextual blend weights from historical analysed matches.

Requirements:
    pip install psycopg2-binary scikit-learn numpy

Usage:
    python calibrate_weights.py

What it does:
  1. Queries fixture_contextual_analysis joined with fixtures that have a
     final result (status_short = 'FT') and a stored base prediction snapshot.
  2. Builds a design matrix of 7 contextual deltas (home - away per factor)
     plus the base logit as an offset column.
  3. Fits a multinomial logistic regression where the target is the actual
     outcome (1=home win, 0=draw, -1=away win encoded as 3-class).
  4. Extracts the per-factor coefficient and stores it in
     contextual_weight_config with today's date and sample count.

Minimum recommended samples: 30. Script exits early if below threshold.
"""

import os
import sys
import math
import datetime
import numpy as np

# Force libpq to use UTF-8 for the entire connection (must be set before psycopg2 opens socket)
os.environ["PGCLIENTENCODING"] = "UTF8"
os.environ["PYTHONIOENCODING"] = "utf-8"

import psycopg2
from psycopg2.extras import RealDictCursor

from db_config import DB_CONFIG

MIN_SAMPLES = 30

FACTOR_COLS = [
    ("home_current_form",       "away_current_form"),
    ("home_team_needs",         "away_team_needs"),
    ("home_defensive_block",    "away_defensive_block"),
    ("home_offensive_rhythm",   "away_offensive_rhythm"),
    ("away_fatigue",            "home_fatigue"),      # inverted: away - home
    ("home_set_pieces",         "away_set_pieces"),
    ("home_stadium_atmosphere", "away_stadium_atmosphere"),
]
FACTOR_NAMES = ["w_forma", "w_needs", "w_def", "w_off", "w_fatigue", "w_set_pieces", "w_atm"]


def logit(p: float) -> float:
    p = max(1e-6, min(1 - 1e-6, p))
    return math.log(p / (1 - p))


def fetch_training_rows(conn) -> list[dict]:
    sql = """
        SELECT
            ca.home_current_form,   ca.away_current_form,
            ca.home_team_needs,     ca.away_team_needs,
            ca.home_defensive_block,ca.away_defensive_block,
            ca.home_offensive_rhythm,ca.away_offensive_rhythm,
            ca.home_fatigue,        ca.away_fatigue,
            ca.home_set_pieces,     ca.away_set_pieces,
            ca.home_stadium_atmosphere, ca.away_stadium_atmosphere,
            ca.base_home_win,
            ca.base_draw,
            ca.base_away_win,
            f.goals_home,
            f.goals_away
        FROM fixture_contextual_analysis ca
        JOIN fixture f ON f.id = ca.fixture_id
        WHERE f.status_short = 'FT'
          AND f.goals_home IS NOT NULL
          AND f.goals_away IS NOT NULL
          AND ca.base_home_win IS NOT NULL
          AND ca.base_draw    IS NOT NULL
          AND ca.base_away_win IS NOT NULL
    """
    with conn.cursor(cursor_factory=RealDictCursor) as cur:
        cur.execute(sql)
        return cur.fetchall()


def build_xy(rows: list[dict]):
    X, y = [], []
    for r in rows:
        deltas = []
        for home_col, away_col in FACTOR_COLS:
            h = r[home_col] if r[home_col] is not None else 3
            a = r[away_col] if r[away_col] is not None else 3
            deltas.append(h - a)

        bH = float(r["base_home_win"])
        bD = float(r["base_draw"])
        bA = float(r["base_away_win"])
        # Offset: log of base probabilities
        deltas.extend([logit(bH), logit(bD), logit(bA)])
        X.append(deltas)

        gh, ga = int(r["goals_home"]), int(r["goals_away"])
        if gh > ga:
            y.append(0)   # home win
        elif ga > gh:
            y.append(2)   # away win
        else:
            y.append(1)   # draw

    return np.array(X, dtype=float), np.array(y, dtype=int)


def save_weights(conn, weights: dict, n_samples: int) -> None:
    sql = """
        INSERT INTO contextual_weight_config
            (w_forma, w_needs, w_def, w_off, w_fatigue, w_set_pieces, w_atm,
             calibration_date, n_samples, notes)
        VALUES
            (%(w_forma)s, %(w_needs)s, %(w_def)s, %(w_off)s,
             %(w_fatigue)s, %(w_set_pieces)s, %(w_atm)s,
             %(calibration_date)s, %(n_samples)s, %(notes)s)
    """
    weights["calibration_date"] = datetime.date.today()
    weights["n_samples"] = n_samples
    weights["notes"] = f"Calibrated automatically on {datetime.date.today()} with {n_samples} samples"
    with conn.cursor() as cur:
        cur.execute(sql, weights)
    conn.commit()


def main():
    try:
        conn = psycopg2.connect(**DB_CONFIG)
    except Exception as e:
        print(f"ERROR: Cannot connect to DB: {e}", file=sys.stderr)
        sys.exit(1)

    rows = fetch_training_rows(conn)
    n = len(rows)
    print(f"Found {n} analysed + completed fixtures with base snapshot.")

    if n < MIN_SAMPLES:
        print(f"Need at least {MIN_SAMPLES} samples (have {n}). Skipping calibration.")
        conn.close()
        sys.exit(0)

    X, y = build_xy(rows)

    try:
        from sklearn.linear_model import LogisticRegression
    except ImportError:
        print("ERROR: scikit-learn not installed. Run: pip install scikit-learn", file=sys.stderr)
        sys.exit(1)

    clf = LogisticRegression(
        multi_class="multinomial",
        solver="lbfgs",
        max_iter=1000,
        C=1.0,
        fit_intercept=False,
    )
    clf.fit(X[:, :7], y)

    # coef_ shape: (3 classes, 7 features) — average signed effect across home/draw/away
    # For the blend we want: how much does factor increase home-win log-prob vs away-win?
    # Use coef[0] (home win) - coef[2] (away win) per factor, divided by 2 for scale
    coef_home = clf.coef_[0][:7]
    coef_away = clf.coef_[2][:7]
    blend_weights = (coef_home - coef_away) / 2.0

    weights = {name: float(round(w, 4)) for name, w in zip(FACTOR_NAMES, blend_weights)}

    print("\nCalibrated weights:")
    for k, v in weights.items():
        print(f"  {k:20s} = {v:+.4f}")

    save_weights(conn, weights, n)
    conn.close()
    print(f"\nWeights saved to contextual_weight_config ({n} samples).")
    print("Restart the Spring Boot backend to pick up new weights from the DB.")


if __name__ == "__main__":
    main()
