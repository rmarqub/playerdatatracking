"""
Fase 4: FastAPI service para predicción de partidos.

Carga los tres modelos .pkl al arrancar y expone dos endpoints:
  GET  /health  → estado del servicio y modelos cargados
  POST /predict → predicción dado un fixture_id

Computa features en tiempo real desde PostgreSQL replicando
exactamente la lógica de feature_engineering.py.

Uso:
    uvicorn predict_api:app --host 0.0.0.0 --port 8001 --reload
"""

import pickle
import re
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any, Optional

import numpy as np
import pandas as pd
import psycopg2
from fastapi import FastAPI, HTTPException
from psycopg2.extras import RealDictCursor
from pydantic import BaseModel

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------

DB_CONFIG = {
    "host":     "localhost",
    "port":     5432,
    "dbname":   "playerdata",
    "user":     "postgres",
    "password": "admin",
}

MODELS_DIR = Path(__file__).parent / "models"

# Populated at startup; keys: "lgbm_1x2", "lgbm_ou25", "lgbm_btts"
MODELS: dict[str, dict] = {}

# Venue-split columns — must match what feature_engineering.py produces
VENUE_ROLL_COLS = ["goals_for", "goals_against", "won", "xg_for", "shots_on_goal"]

# All rolling columns — must match ROLL_COLS in feature_engineering.py
ALL_ROLL_COLS = [
    "goals_for", "goals_against", "won", "drew", "lost",
    "xg_for", "shots_on_goal", "shots_total",
    "possession", "passes_pct", "corner_kicks", "saves",
]


# ---------------------------------------------------------------------------
# Startup
# ---------------------------------------------------------------------------

@asynccontextmanager
async def lifespan(app: FastAPI):
    for name in ("lgbm_1x2", "lgbm_ou25", "lgbm_btts"):
        path = MODELS_DIR / f"{name}.pkl"
        if not path.exists():
            raise RuntimeError(f"Modelo no encontrado: {path}. Ejecuta train_model.py primero.")
        with open(path, "rb") as f:
            MODELS[name] = pickle.load(f)
    print(f"Modelos cargados: {list(MODELS)}")
    yield


app = FastAPI(title="Match Prediction API", version="1.0", lifespan=lifespan)


# ---------------------------------------------------------------------------
# Schemas
# ---------------------------------------------------------------------------

class PredictRequest(BaseModel):
    fixture_id: int


# ---------------------------------------------------------------------------
# DB helpers
# ---------------------------------------------------------------------------

def _get_conn():
    return psycopg2.connect(**DB_CONFIG, cursor_factory=RealDictCursor)


def _infer_lookback(features: list[str]) -> int:
    """Infiere el tamaño de ventana rolling desde los nombres de features."""
    nums = {int(m.group(1)) for f in features if (m := re.search(r"_last(\d+)$", f))}
    return max(nums) if nums else 5


# ---------------------------------------------------------------------------
# Queries
# ---------------------------------------------------------------------------

def _query_fixture(conn, fixture_id: int) -> dict:
    with conn.cursor() as cur:
        cur.execute("""
            SELECT id, league_id, league_name, season, match_date,
                   home_team_id, home_team_name,
                   away_team_id, away_team_name,
                   goals_home, goals_away, status_short
            FROM fixture
            WHERE id = %s
        """, (fixture_id,))
        row = cur.fetchone()
    if not row:
        raise HTTPException(status_code=404, detail=f"Fixture {fixture_id} no encontrado")
    return dict(row)


def _query_team_matches(
    conn,
    team_id: int,
    match_date: Any,
    n: int,
    venue_filter: Optional[str] = None,
) -> pd.DataFrame:
    """Últimos N partidos FT para un equipo, opcionalmente filtrados por local/visitante."""
    if venue_filter == "H":
        team_clause = "AND f.home_team_id = %(tid)s"
    elif venue_filter == "A":
        team_clause = "AND f.away_team_id = %(tid)s"
    else:
        team_clause = "AND (f.home_team_id = %(tid)s OR f.away_team_id = %(tid)s)"

    sql = f"""
        SELECT
            CASE WHEN f.home_team_id = %(tid)s THEN f.goals_home ELSE f.goals_away END AS goals_for,
            CASE WHEN f.home_team_id = %(tid)s THEN f.goals_away ELSE f.goals_home END AS goals_against,
            ts.shots_on_goal,
            ts.shots_total,
            ts.ball_possession  AS possession,
            ts.passes_pct,
            ts.corner_kicks,
            ts.goalkeeper_saves AS saves,
            ts.expected_goals   AS xg_for
        FROM fixture f
        LEFT JOIN fixture_team_stats ts
               ON ts.fixture_id = f.id AND ts.team_id = %(tid)s
        WHERE f.status_short = 'FT'
          {team_clause}
          AND f.match_date < %(dt)s
        ORDER BY f.match_date DESC
        LIMIT %(n)s
    """
    with conn.cursor() as cur:
        cur.execute(sql, {"tid": team_id, "dt": match_date, "n": n})
        rows = cur.fetchall()
    if not rows:
        return pd.DataFrame()
    df = pd.DataFrame([dict(r) for r in rows])
    df["won"]  = (df["goals_for"] > df["goals_against"]).astype(float)
    df["drew"] = (df["goals_for"] == df["goals_against"]).astype(float)
    df["lost"] = (df["goals_for"] < df["goals_against"]).astype(float)
    return df


def _query_season_form(conn, team_id: int, season: int, match_date: Any) -> dict:
    """PPG, GFPG, GAPG de temporada con partidos FT anteriores a match_date."""
    with conn.cursor() as cur:
        cur.execute("""
            SELECT
                CASE WHEN f.home_team_id = %(tid)s THEN f.goals_home ELSE f.goals_away END AS gf,
                CASE WHEN f.home_team_id = %(tid)s THEN f.goals_away ELSE f.goals_home END AS ga
            FROM fixture f
            WHERE f.status_short = 'FT'
              AND (f.home_team_id = %(tid)s OR f.away_team_id = %(tid)s)
              AND f.season = %(season)s
              AND f.match_date < %(dt)s
        """, {"tid": team_id, "season": season, "dt": match_date})
        rows = cur.fetchall()

    if not rows:
        return {"season_ppg": np.nan, "season_gfpg": np.nan, "season_gapg": np.nan, "season_games": 0}

    df = pd.DataFrame([dict(r) for r in rows])
    games = len(df)
    pts = ((df["gf"] > df["ga"]) * 3 + (df["gf"] == df["ga"]) * 1).sum()
    return {
        "season_ppg":   float(pts / games),
        "season_gfpg":  float(df["gf"].mean()),
        "season_gapg":  float(df["ga"].mean()),
        "season_games": games,
    }


def _query_h2h(conn, home_id: int, away_id: int, match_date: Any, m: int) -> dict:
    """Últimos M enfrentamientos directos entre los dos equipos."""
    with conn.cursor() as cur:
        cur.execute("""
            SELECT home_team_id, away_team_id, goals_home, goals_away
            FROM fixture
            WHERE status_short = 'FT'
              AND ((home_team_id = %(h)s AND away_team_id = %(a)s)
                OR (home_team_id = %(a)s AND away_team_id = %(h)s))
              AND match_date < %(dt)s
            ORDER BY match_date DESC
            LIMIT %(m)s
        """, {"h": home_id, "a": away_id, "dt": match_date, "m": m})
        rows = cur.fetchall()

    if not rows:
        return {"h2h_home_wins": np.nan, "h2h_draws": np.nan,
                "h2h_away_wins": np.nan, "h2h_avg_goals": np.nan, "h2h_count": 0}

    df = pd.DataFrame([dict(r) for r in rows])
    n = len(df)
    home_wins = sum(
        (r.home_team_id == home_id and r.goals_home > r.goals_away) or
        (r.away_team_id == home_id and r.goals_away > r.goals_home)
        for r in df.itertuples(index=False)
    )
    draws     = int((df["goals_home"] == df["goals_away"]).sum())
    away_wins = n - home_wins - draws
    return {
        "h2h_home_wins": home_wins / n,
        "h2h_draws":     draws / n,
        "h2h_away_wins": away_wins / n,
        "h2h_avg_goals": float((df["goals_home"] + df["goals_away"]).mean()),
        "h2h_count":     n,
    }


def _query_days_rest(conn, team_id: int, match_date: Any) -> float:
    """Días desde el último partido del equipo."""
    with conn.cursor() as cur:
        cur.execute("""
            SELECT MAX(match_date) AS last_match
            FROM fixture
            WHERE status_short = 'FT'
              AND (home_team_id = %(tid)s OR away_team_id = %(tid)s)
              AND match_date < %(dt)s
        """, {"tid": team_id, "dt": match_date})
        row = cur.fetchone()
    if row and row["last_match"]:
        delta = pd.Timestamp(match_date) - pd.Timestamp(row["last_match"])
        return float(delta.days)
    return np.nan


# ---------------------------------------------------------------------------
# Feature vector construction
# ---------------------------------------------------------------------------

def _rolling_mean(df: pd.DataFrame, cols: list[str]) -> dict:
    """Media de las columnas disponibles sobre todos los partidos del df."""
    result = {}
    for col in cols:
        if col in df.columns:
            val = df[col].mean()
            result[f"roll_{col}"] = float(val) if not pd.isna(val) else np.nan
    return result


def build_feature_row(
    fixture_id: int,
    features: list[str],
    n: int,
    m: int,
) -> tuple[pd.DataFrame, list[str], dict]:
    """
    Construye la fila de features para un partido y la alinea con el schema
    de entrenamiento. Devuelve (df_1row, warnings, fixture_info).
    """
    warnings: list[str] = []
    conn = _get_conn()

    try:
        fix = _query_fixture(conn, fixture_id)
        match_date = fix["match_date"]
        home_id    = fix["home_team_id"]
        away_id    = fix["away_team_id"]
        season     = fix["season"]

        row: dict = {"league_id": fix["league_id"], "season": season}

        # ---- Rolling all-venue ----
        home_matches = _query_team_matches(conn, home_id, match_date, n)
        away_matches = _query_team_matches(conn, away_id, match_date, n)

        if len(home_matches) < 3:
            warnings.append(f"Equipo local con solo {len(home_matches)} partidos previos (mín. recomendado: 3)")
        if len(away_matches) < 3:
            warnings.append(f"Equipo visitante con solo {len(away_matches)} partidos previos (mín. recomendado: 3)")

        for col, val in _rolling_mean(home_matches, ALL_ROLL_COLS).items():
            row[f"home_{col}_last{n}"] = val
        for col, val in _rolling_mean(away_matches, ALL_ROLL_COLS).items():
            row[f"away_{col}_last{n}"] = val

        # ---- Rolling venue-split ----
        home_h = _query_team_matches(conn, home_id, match_date, n, venue_filter="H")
        away_a = _query_team_matches(conn, away_id, match_date, n, venue_filter="A")

        for col in VENUE_ROLL_COLS:
            if not home_h.empty and col in home_h.columns:
                row[f"roll_{col}_h_last{n}"] = float(home_h[col].mean())
            if not away_a.empty and col in away_a.columns:
                row[f"roll_{col}_a_last{n}"] = float(away_a[col].mean())

        # ---- Days rest ----
        row["home_days_rest"] = _query_days_rest(conn, home_id, match_date)
        row["away_days_rest"] = _query_days_rest(conn, away_id, match_date)

        # ---- Season form ----
        home_sf = _query_season_form(conn, home_id, season, match_date)
        away_sf = _query_season_form(conn, away_id, season, match_date)

        row["home_season_ppg"]   = home_sf["season_ppg"]
        row["home_season_gfpg"]  = home_sf["season_gfpg"]
        row["home_season_gapg"]  = home_sf["season_gapg"]
        row["home_season_games"] = home_sf["season_games"]
        row["away_season_ppg"]   = away_sf["season_ppg"]
        row["away_season_gfpg"]  = away_sf["season_gfpg"]
        row["away_season_gapg"]  = away_sf["season_gapg"]
        row["away_season_games"] = away_sf["season_games"]

        if home_sf["season_games"] == 0:
            warnings.append("El equipo local no tiene partidos previos en esta temporada — season form será NaN")
        if away_sf["season_games"] == 0:
            warnings.append("El equipo visitante no tiene partidos previos en esta temporada — season form será NaN")

        # ---- H2H ----
        h2h = _query_h2h(conn, home_id, away_id, match_date, m)
        row.update(h2h)
        if h2h["h2h_count"] == 0:
            warnings.append("Sin historial H2H entre estos equipos — features H2H serán NaN")

        # ---- Diff features ----
        diff_pairs = [
            (f"home_roll_goals_for_last{n}",     f"away_roll_goals_for_last{n}",     "diff_goals_for"),
            (f"home_roll_goals_against_last{n}", f"away_roll_goals_against_last{n}", "diff_goals_against"),
            (f"home_roll_won_last{n}",           f"away_roll_won_last{n}",           "diff_wins"),
            (f"home_roll_shots_on_goal_last{n}", f"away_roll_shots_on_goal_last{n}", "diff_shots_on_goal"),
            (f"home_roll_possession_last{n}",    f"away_roll_possession_last{n}",    "diff_possession"),
            (f"home_roll_passes_pct_last{n}",    f"away_roll_passes_pct_last{n}",    "diff_passes_pct"),
            (f"home_roll_xg_for_last{n}",        f"away_roll_xg_for_last{n}",        "diff_xg"),
            ("home_season_ppg",                  "away_season_ppg",                  "diff_season_ppg"),
            ("home_season_gfpg",                 "away_season_gfpg",                 "diff_season_gfpg"),
            ("home_season_gapg",                 "away_season_gapg",                 "diff_season_gapg"),
        ]
        for col_h, col_a, name in diff_pairs:
            h_val = row.get(col_h)
            a_val = row.get(col_a)
            if (h_val is not None and a_val is not None
                    and not pd.isna(h_val) and not pd.isna(a_val)):
                row[name] = h_val - a_val
            else:
                row[name] = np.nan

    finally:
        conn.close()

    # Alinear al schema exacto de entrenamiento
    df = pd.DataFrame([row])
    for col in features:
        if col not in df.columns:
            df[col] = np.nan

    for col in ("league_id", "season"):
        if col in df.columns:
            df[col] = df[col].astype("category")

    return df[features], warnings, fix


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------

@app.get("/health")
def health():
    return {
        "status":        "ok",
        "models_loaded": list(MODELS.keys()),
    }


@app.post("/predict")
def predict(req: PredictRequest):
    features = MODELS["lgbm_1x2"]["metadata"]["features"]
    n = _infer_lookback(features)
    m = 5  # H2H lookback

    df, warnings, fix = build_feature_row(req.fixture_id, features, n, m)

    # 1X2
    model_1x2  = MODELS["lgbm_1x2"]["model"]
    probs_1x2  = model_1x2.predict_proba(df)[0]
    pred_1x2   = int(np.argmax(probs_1x2))
    label_map  = {0: "home_win", 1: "draw", 2: "away_win"}
    sorted_p   = sorted(probs_1x2, reverse=True)
    confidence = round(float(sorted_p[0] - sorted_p[1]), 4)

    # Over/Under 2.5
    model_ou  = MODELS["lgbm_ou25"]["model"]
    probs_ou  = model_ou.predict_proba(df)[0]
    pred_over = bool(probs_ou[1] >= 0.5)

    # BTTS
    model_btts = MODELS["lgbm_btts"]["model"]
    probs_btts = model_btts.predict_proba(df)[0]
    pred_btts  = bool(probs_btts[1] >= 0.5)

    return {
        "fixture_id": req.fixture_id,
        "home_team":  fix["home_team_name"],
        "away_team":  fix["away_team_name"],
        "league":     fix["league_name"],
        "season":     fix["season"],
        "match_date": str(fix["match_date"]),
        "status":     fix["status_short"],
        "result_1x2": {
            "home_win":   round(float(probs_1x2[0]), 4),
            "draw":       round(float(probs_1x2[1]), 4),
            "away_win":   round(float(probs_1x2[2]), 4),
            "predicted":  label_map[pred_1x2],
            "confidence": confidence,
        },
        "over_under_25": {
            "over":      round(float(probs_ou[1]), 4),
            "under":     round(float(probs_ou[0]), 4),
            "predicted": "over" if pred_over else "under",
        },
        "btts": {
            "yes":       round(float(probs_btts[1]), 4),
            "no":        round(float(probs_btts[0]), 4),
            "predicted": "yes" if pred_btts else "no",
        },
        "warnings": warnings,
    }
