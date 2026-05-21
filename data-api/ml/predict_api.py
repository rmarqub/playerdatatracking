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
import sys
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any, Optional
from urllib.parse import quote, quote_plus

import numpy as np
import pandas as pd
import psycopg2
from scipy.stats import poisson as scipy_poisson
import compute_season_percentiles as _compute_pct
import compute_player_percentiles as _compute_pp
from fastapi import BackgroundTasks, FastAPI, HTTPException
from psycopg2.extras import RealDictCursor
from pydantic import BaseModel
from train_model import CalibratedLGBM


class PatchedUnpickler(pickle.Unpickler):
    def find_class(self, module: str, name: str):
        if name == "CalibratedLGBM":
            return CalibratedLGBM
        return super().find_class(module, name)


def _load_model(path: Path) -> dict:
    """Carga modelo pickle con soporte para CalibratedLGBM."""
    with open(path, "rb") as f:
        return PatchedUnpickler(f).load()

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------

from db_config import DB_CONFIG

MODELS_DIR = Path(__file__).parent / "models"

# Populated at startup; keys: "lgbm_1x2", "lgbm_btts", "lgbm_goals_lambda", "lgbm_corners_lambda"
MODELS: dict[str, dict] = {}

# Venue-split columns — must match what feature_engineering.py produces
VENUE_ROLL_COLS = ["goals_for", "goals_against", "won", "xg_for", "shots_on_goal"]

# All rolling columns — must match ROLL_COLS in feature_engineering.py
ALL_ROLL_COLS = [
    "goals_for", "goals_against", "won", "drew", "lost",
    "scored", "clean_sheet",
    "xg_for", "xg_against", "shots_on_goal", "shots_total",
    "possession", "passes_pct", "corner_kicks", "saves",
    "shooting_accuracy", "shots_inside_box_rate",
    "corners_against", "corner_ratio",
    "fouls_per_shot", "yellow_cards", "fouls",
    "btts", "ou25", "ou15",
]

# EMA columns — must match EMA_COLS in feature_engineering.py
EMA_COLS = ["goals_for", "goals_against", "won", "scored", "clean_sheet", "xg_for", "xg_against", "shots_on_goal"]

# Player rolling columns — must match PLAYER_ROLL_COLS in feature_engineering.py
# lineup_continuity is excluded here — computed via a separate query (_query_team_lineup_continuity)
PLAYER_ROLL_COLS = [
    "avg_rating",
    "goals_pstarted",
    "key_passes_pstarted",
    "def_actions_pstarted",
    "duel_win_pct",
    "gk_avg_rating",
    "gk_save_pct",
    "avg_rating_d",
    "avg_rating_m",
    "avg_rating_f",
    "max_scorer_goals",
    "goals_concentration",
]


# ---------------------------------------------------------------------------
# Startup
# ---------------------------------------------------------------------------

@asynccontextmanager
async def lifespan(app: FastAPI):
    for name in ("lgbm_1x2", "lgbm_btts", "lgbm_goals_lambda", "lgbm_corners_lambda"):
        path = MODELS_DIR / f"{name}.pkl"
        if not path.exists():
            raise RuntimeError(f"Modelo no encontrado: {path}. Ejecuta train_model.py primero.")
        MODELS[name] = _load_model(path)
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
    # URL con percent-encoding en credenciales. lc_messages=C se pasa via
    # el parámetro "options" (único mecanismo válido en la URI de libpq)
    # para forzar mensajes de error en ASCII y evitar UnicodeDecodeError
    # en Windows con PostgreSQL en español (cp1252).
    cfg = DB_CONFIG
    options = quote("-c lc_messages=C", safe="")
    url = (
        f"postgresql://{quote_plus(str(cfg['user']))}:"
        f"{quote_plus(str(cfg['password']))}@"
        f"{cfg['host']}:{cfg['port']}/{cfg['dbname']}"
        f"?client_encoding=UTF8&options={options}"
    )
    return psycopg2.connect(url, cursor_factory=RealDictCursor)


def _infer_lookback(features: list[str]) -> int:
    """Infiere el tamaño de ventana rolling desde los nombres de features."""
    nums = {int(m.group(1)) for f in features if (m := re.search(r"_last(\d+)$", f))}
    return max(nums) if nums else 5


# ---------------------------------------------------------------------------
# Queries — team stats
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
            ts.ball_possession      AS possession,
            ts.passes_pct,
            ts.corner_kicks,
            ts.goalkeeper_saves     AS saves,
            ts.expected_goals       AS xg_for,
            ts.shots_inside_box,
            ts.fouls,
            ts.yellow_cards,
            ts_rival.expected_goals AS xg_against,
            ts_rival.corner_kicks   AS corners_against
        FROM fixture f
        LEFT JOIN fixture_team_stats ts
               ON ts.fixture_id = f.id AND ts.team_id = %(tid)s
        LEFT JOIN fixture_team_stats ts_rival
               ON ts_rival.fixture_id = f.id
              AND ts_rival.team_id = CASE
                  WHEN f.home_team_id = %(tid)s THEN f.away_team_id
                  ELSE f.home_team_id
              END
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
    df["won"]         = (df["goals_for"] > df["goals_against"]).astype(float)
    df["drew"]        = (df["goals_for"] == df["goals_against"]).astype(float)
    df["lost"]        = (df["goals_for"] < df["goals_against"]).astype(float)
    df["scored"]      = (df["goals_for"] > 0).astype(float)
    df["clean_sheet"] = (df["goals_against"] == 0).astype(float)
    df["btts"]        = ((df["goals_for"] > 0) & (df["goals_against"] > 0)).astype(float)
    df["ou25"]        = ((df["goals_for"] + df["goals_against"]) > 2.5).astype(float)
    df["ou15"]        = ((df["goals_for"] + df["goals_against"]) > 1.5).astype(float)
    shots_total_safe       = df["shots_total"].replace(0, np.nan)
    df["shooting_accuracy"]     = df["shots_on_goal"] / shots_total_safe
    df["shots_inside_box_rate"] = df["shots_inside_box"] / shots_total_safe
    df["fouls_per_shot"]        = df["fouls"] / shots_total_safe
    corners_total = df["corner_kicks"].fillna(0) + df["corners_against"].fillna(0)
    df["corner_ratio"]          = df["corner_kicks"] / (corners_total + 1e-6)
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
# Queries — league base rates & EMA
# ---------------------------------------------------------------------------

def _query_league_rates(conn, league_id: int, match_date: Any) -> dict:
    """League-level historical rates usando todos los partidos FT anteriores en esa liga."""
    with conn.cursor() as cur:
        cur.execute("""
            SELECT
                AVG(CASE WHEN f.goals_home > f.goals_away THEN 1.0 ELSE 0.0 END)          AS league_home_win_rate,
                AVG(f.goals_home + f.goals_away)                                             AS league_avg_goals,
                AVG(CASE WHEN f.goals_home + f.goals_away > 2.5 THEN 1.0 ELSE 0.0 END)    AS league_over25_rate,
                AVG(CASE WHEN f.goals_home + f.goals_away > 1.5 THEN 1.0 ELSE 0.0 END)    AS league_over15_rate,
                AVG(CASE WHEN f.goals_home > 0 AND f.goals_away > 0 THEN 1.0 ELSE 0.0 END) AS league_btts_rate,
                AVG(ck.total_ck)                                                             AS league_avg_corners,
                COUNT(*) AS match_count
            FROM fixture f
            LEFT JOIN (
                SELECT fixture_id, SUM(corner_kicks) AS total_ck
                FROM fixture_team_stats
                WHERE corner_kicks IS NOT NULL
                GROUP BY fixture_id
            ) ck ON ck.fixture_id = f.id
            WHERE f.status_short = 'FT'
              AND f.league_id = %(lid)s
              AND f.match_date < %(dt)s
        """, {"lid": league_id, "dt": match_date})
        row = cur.fetchone()

    if not row or not row["match_count"] or row["match_count"] < 10:
        return {
            "league_home_win_rate": np.nan, "league_avg_goals": np.nan,
            "league_over25_rate": np.nan, "league_over15_rate": np.nan,
            "league_btts_rate": np.nan, "league_avg_corners": np.nan,
        }

    def _f(v):
        return float(v) if v is not None else np.nan

    return {
        "league_home_win_rate": _f(row["league_home_win_rate"]),
        "league_avg_goals":     _f(row["league_avg_goals"]),
        "league_over25_rate":   _f(row["league_over25_rate"]),
        "league_over15_rate":   _f(row["league_over15_rate"]),
        "league_btts_rate":     _f(row["league_btts_rate"]),
        "league_avg_corners":   _f(row["league_avg_corners"]),
    }


def _compute_ema_from_matches(df: pd.DataFrame, span: int, prefix: str) -> dict:
    """
    Calcula EMA sobre los partidos ya cargados (en orden DESC de fecha → invertimos).
    Se usan las últimas 3*span filas — suficiente para que la EMA haya convergido.
    """
    if df.empty:
        return {f"{prefix}_ema_{col}_span{span}": np.nan for col in EMA_COLS}

    # df viene en orden DESC (más reciente primero) — invertimos para EMA correcta
    df_asc = df.iloc[::-1].reset_index(drop=True)
    result = {}
    for col in EMA_COLS:
        if col in df_asc.columns:
            val = df_asc[col].ewm(span=span, min_periods=1, adjust=False).mean().iloc[-1]
            result[f"{prefix}_ema_{col}_span{span}"] = float(val) if not pd.isna(val) else np.nan
        else:
            result[f"{prefix}_ema_{col}_span{span}"] = np.nan
    return result


# ---------------------------------------------------------------------------
# Queries — player stats
# ---------------------------------------------------------------------------

def _query_team_player_form(conn, team_id: int, match_date: Any, n: int) -> dict:
    """
    Últimos N partidos FT del equipo con stats de titulares agregados por partido.
    Replica build_team_player_history + compute_rolling_player_features.
    Incluye: métricas globales, positionales (GK/D/M/F), top scorer.
    lineup_continuity se calcula por separado en _query_team_lineup_continuity.
    """
    with conn.cursor() as cur:
        cur.execute("""
            SELECT
                f.id AS fixture_id,
                -- Globales
                AVG(ps.rating::float) FILTER (WHERE ps.substitute = false)
                    AS avg_rating,
                SUM(ps.goals_scored) FILTER (WHERE ps.substitute = false)::float
                    / NULLIF(COUNT(*) FILTER (WHERE ps.substitute = false), 0)
                    AS goals_pstarted,
                SUM(ps.passes_key) FILTER (WHERE ps.substitute = false)::float
                    / NULLIF(COUNT(*) FILTER (WHERE ps.substitute = false), 0)
                    AS key_passes_pstarted,
                (COALESCE(SUM(ps.tackles_total)   FILTER (WHERE ps.substitute = false AND ps.position != 'G'), 0)
                 + COALESCE(SUM(ps.interceptions) FILTER (WHERE ps.substitute = false AND ps.position != 'G'), 0))::float
                    / NULLIF(COUNT(*) FILTER (WHERE ps.substitute = false AND ps.position != 'G'), 0)
                    AS def_actions_pstarted,
                SUM(ps.duels_won) FILTER (WHERE ps.substitute = false)::float
                    / NULLIF(SUM(ps.duels_total) FILTER (WHERE ps.substitute = false), 0)
                    AS duel_win_pct,
                COUNT(*) FILTER (WHERE ps.substitute = false)
                    AS n_starters,
                -- Portero
                AVG(ps.rating::float) FILTER (WHERE ps.substitute = false AND ps.position = 'G')
                    AS gk_avg_rating,
                SUM(ps.saves) FILTER (WHERE ps.substitute = false AND ps.position = 'G')::float
                    / NULLIF(
                        SUM(ps.saves)         FILTER (WHERE ps.substitute = false AND ps.position = 'G')
                      + SUM(ps.goals_conceded) FILTER (WHERE ps.substitute = false AND ps.position = 'G'),
                      0)
                    AS gk_save_pct,
                -- Por posición
                AVG(ps.rating::float) FILTER (WHERE ps.substitute = false AND ps.position = 'D')
                    AS avg_rating_d,
                AVG(ps.rating::float) FILTER (WHERE ps.substitute = false AND ps.position = 'M')
                    AS avg_rating_m,
                AVG(ps.rating::float) FILTER (WHERE ps.substitute = false AND ps.position = 'F')
                    AS avg_rating_f,
                -- Top scorer
                MAX(ps.goals_scored) FILTER (WHERE ps.substitute = false)
                    AS max_scorer_goals,
                MAX(ps.goals_scored) FILTER (WHERE ps.substitute = false)::float
                    / NULLIF(SUM(ps.goals_scored) FILTER (WHERE ps.substitute = false), 0)
                    AS goals_concentration
            FROM fixture f
            JOIN fixture_player_stats ps ON ps.fixture_id = f.id AND ps.team_id = %(tid)s
            WHERE f.status_short = 'FT'
              AND f.match_date < %(dt)s
            GROUP BY f.id, f.match_date
            ORDER BY f.match_date DESC
            LIMIT %(n)s
        """, {"tid": team_id, "dt": match_date, "n": n})
        rows = cur.fetchall()

    empty = {f"roll_player_{col}_last{n}": np.nan for col in PLAYER_ROLL_COLS}
    if not rows:
        return empty

    df = pd.DataFrame([dict(r) for r in rows])
    # Descarta partidos con < 6 titulares (datos insuficientes)
    for col in PLAYER_ROLL_COLS:
        if col in df.columns:
            df.loc[df["n_starters"] < 6, col] = np.nan

    result = {}
    for col in PLAYER_ROLL_COLS:
        if col in df.columns:
            val = df[col].mean()
            result[f"roll_player_{col}_last{n}"] = float(val) if not pd.isna(val) else np.nan
        else:
            result[f"roll_player_{col}_last{n}"] = np.nan
    return result


def _query_team_lineup_continuity(conn, team_id: int, match_date: Any, n: int) -> dict:
    """
    Rolling average de continuidad de alineación en los últimos n partidos consecutivos.
    Replica la lógica de _compute_lineup_continuity + compute_rolling_player_features.
    """
    key = f"roll_player_lineup_continuity_last{n}"

    with conn.cursor() as cur:
        cur.execute("""
            SELECT f.id, f.match_date
            FROM fixture f
            WHERE f.status_short = 'FT'
              AND (f.home_team_id = %(tid)s OR f.away_team_id = %(tid)s)
              AND f.match_date < %(dt)s
            ORDER BY f.match_date DESC
            LIMIT %(lim)s
        """, {"tid": team_id, "dt": match_date, "lim": n + 1})
        recent = [(r["id"], r["match_date"]) for r in cur.fetchall()]

    if len(recent) < 2:
        return {key: np.nan}

    fixture_ids = [fid for fid, _ in recent]

    with conn.cursor() as cur:
        cur.execute("""
            SELECT fixture_id, player_id
            FROM fixture_player_stats
            WHERE fixture_id = ANY(%s)
              AND team_id = %s
              AND substitute = false
        """, (fixture_ids, team_id))
        rows = cur.fetchall()

    if not rows:
        return {key: np.nan}

    df = pd.DataFrame([dict(r) for r in rows])
    # fixture_order: 0 = más reciente
    fix_order = {fid: i for i, (fid, _) in enumerate(recent)}
    sets = df.groupby("fixture_id")["player_id"].apply(set).reset_index()
    sets["order"] = sets["fixture_id"].map(fix_order)
    sets = sets.sort_values("order").reset_index(drop=True)

    continuities = []
    for i in range(len(sets) - 1):
        s1 = sets.at[i, "player_id"]
        s2 = sets.at[i + 1, "player_id"]
        if s1:
            continuities.append(len(s1 & s2) / max(len(s1), 11))

    val = float(np.mean(continuities)) if continuities else np.nan
    return {key: val}


def _query_lineup_percentile_features(
    conn, team_id: int, match_date: Any, league_id: int, season: int
) -> dict:
    """
    Calcula features de percentil de alineación para el equipo en tiempo real.
    Usa los titulares del partido más reciente como proxy del eleven esperado.
    Usa estadísticas de season-1 (temporada anterior) para evitar leakage temporal:
    los percentiles de la temporada en curso se irían computando con datos futuros.
    Fase A — sin impacto de ausencias (eso viene con la integración de unavailable_players).
    """
    empty = {
        "avg_starter_rating_pct":  np.nan,
        "avg_att_goal_pct":        np.nan,
        "top_attacker_goal_pct":   np.nan,
        "avg_att_kp_pct":          np.nan,
        "avg_def_pct":             np.nan,
    }

    # Último partido jugado por el equipo
    with conn.cursor() as cur:
        cur.execute("""
            SELECT f.id
            FROM fixture f
            WHERE (f.home_team_id = %s OR f.away_team_id = %s)
              AND f.status_short = 'FT'
              AND f.match_date < %s
            ORDER BY f.match_date DESC
            LIMIT 1
        """, (team_id, team_id, match_date))
        row = cur.fetchone()

    if not row:
        return empty

    last_fixture_id = row["id"]

    with conn.cursor() as cur:
        cur.execute("""
            SELECT player_id, position
            FROM fixture_player_stats
            WHERE fixture_id = %s AND team_id = %s AND substitute = false
        """, (last_fixture_id, team_id))
        starters = [(r["player_id"], r["position"]) for r in cur.fetchall()]

    if not starters:
        return empty

    starter_ids = [s[0] for s in starters]
    starter_pos = {s[0]: s[1] for s in starters}

    # Lookup en tabla pre-computada: snapshot más reciente antes del partido
    # (poblar con compute_percentiles.py antes de usar la API)
    with conn.cursor() as cur:
        cur.execute("""
            SELECT DISTINCT ON (player_id)
                player_id, goals_p90_pct, kp_p90_pct, def_p90_pct, avg_rating_pct
            FROM player_season_percentiles
            WHERE player_id = ANY(%s)
              AND league_id  = %s
              AND season     = %s
              AND as_of_date <= %s
            ORDER BY player_id, as_of_date DESC
        """, (starter_ids, league_id, season, match_date))
        all_pct = {r["player_id"]: dict(r) for r in cur.fetchall()}

    ratings, att_goals, att_kp, def_pct = [], [], [], []
    for pid, pos in starters:
        p = all_pct.get(pid)
        if not p:
            continue
        if p.get("avg_rating_pct") is not None:
            ratings.append(p["avg_rating_pct"])
        if pos in ("F", "M"):
            if p.get("goals_p90_pct") is not None:
                att_goals.append(p["goals_p90_pct"])
            if p.get("kp_p90_pct") is not None:
                att_kp.append(p["kp_p90_pct"])
        if pos in ("D", "G") and p.get("def_p90_pct") is not None:
            def_pct.append(p["def_p90_pct"])

    return {
        "avg_starter_rating_pct": float(np.mean(ratings))    if ratings    else np.nan,
        "avg_att_goal_pct":       float(np.mean(att_goals))  if att_goals  else np.nan,
        "top_attacker_goal_pct":  float(np.max(att_goals))   if att_goals  else np.nan,
        "avg_att_kp_pct":         float(np.mean(att_kp))     if att_kp     else np.nan,
        "avg_def_pct":            float(np.mean(def_pct))    if def_pct    else np.nan,
    }


def _query_h2h_player_form(conn, home_id: int, away_id: int, match_date: Any, m: int) -> dict:
    """
    Rendimiento de los titulares de cada equipo en los últimos M enfrentamientos directos.
    Replica la lógica de compute_h2h_player_features.
    """
    empty = {
        "h2h_home_avg_rating":     np.nan,
        "h2h_away_avg_rating":     np.nan,
        "h2h_home_goals_pstarted": np.nan,
        "h2h_away_goals_pstarted": np.nan,
    }

    with conn.cursor() as cur:
        cur.execute("""
            WITH h2h_fixtures AS (
                SELECT id
                FROM fixture
                WHERE status_short = 'FT'
                  AND ((home_team_id = %(h)s AND away_team_id = %(a)s)
                    OR (home_team_id = %(a)s AND away_team_id = %(h)s))
                  AND match_date < %(dt)s
                ORDER BY match_date DESC
                LIMIT %(m)s
            )
            SELECT
                ps.team_id,
                AVG(ps.rating::float) FILTER (WHERE ps.substitute = false)       AS avg_rating,
                SUM(ps.goals_scored)  FILTER (WHERE ps.substitute = false)::float
                    / NULLIF(COUNT(*) FILTER (WHERE ps.substitute = false), 0)   AS goals_pstarted
            FROM fixture_player_stats ps
            WHERE ps.fixture_id IN (SELECT id FROM h2h_fixtures)
              AND ps.team_id IN (%(h)s, %(a)s)
            GROUP BY ps.team_id
        """, {"h": home_id, "a": away_id, "dt": match_date, "m": m})
        rows = cur.fetchall()

    if not rows:
        return empty

    result = dict(empty)
    for row in rows:
        r = dict(row)
        tid = r["team_id"]
        avg_r = float(r["avg_rating"]) if r["avg_rating"] is not None else np.nan
        gps   = float(r["goals_pstarted"]) if r["goals_pstarted"] is not None else np.nan
        if tid == home_id:
            result["h2h_home_avg_rating"]     = avg_r
            result["h2h_home_goals_pstarted"] = gps
        elif tid == away_id:
            result["h2h_away_avg_rating"]     = avg_r
            result["h2h_away_goals_pstarted"] = gps
    return result


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


def _align_features(df: pd.DataFrame, features: list[str]) -> pd.DataFrame:
    """Alinea df al schema exacto de un modelo (añade NaN para features ausentes)."""
    for col in features:
        if col not in df.columns:
            df[col] = np.nan
    for col in ("league_id", "season"):
        if col in df.columns and col in features:
            df[col] = df[col].astype("category")
    return df[features]


def build_feature_row(
    fixture_id: int,
    n: int,
    m: int,
) -> tuple[pd.DataFrame, list[str], dict]:
    """
    Construye la fila de features para un partido con todas las columnas disponibles.
    Devuelve (df_all_cols, warnings, fixture_info).
    Usa _align_features(df, model_features) para filtrar al schema de cada modelo.
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

        # ---- EMA features (calculado desde los matches ya cargados, sin query extra) ----
        ema_home = _compute_ema_from_matches(home_matches, n, "home")
        ema_away = _compute_ema_from_matches(away_matches, n, "away")
        row.update(ema_home)
        row.update(ema_away)

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

        # ---- League base rates ----
        league_rates = _query_league_rates(conn, fix["league_id"], match_date)
        row.update(league_rates)

        # ---- H2H team stats ----
        h2h = _query_h2h(conn, home_id, away_id, match_date, m)
        row.update(h2h)
        if h2h["h2h_count"] == 0:
            warnings.append("Sin historial H2H entre estos equipos — features H2H serán NaN")

        # ---- Player rolling features ----
        home_player = _query_team_player_form(conn, home_id, match_date, n)
        away_player = _query_team_player_form(conn, away_id, match_date, n)

        for key, val in home_player.items():
            row[f"home_{key}"] = val
        for key, val in away_player.items():
            row[f"away_{key}"] = val

        # ---- Lineup continuity ----
        home_lc = _query_team_lineup_continuity(conn, home_id, match_date, n)
        away_lc = _query_team_lineup_continuity(conn, away_id, match_date, n)
        for key, val in home_lc.items():
            row[f"home_{key}"] = val
        for key, val in away_lc.items():
            row[f"away_{key}"] = val

        # ---- H2H player features ----
        h2h_player = _query_h2h_player_form(conn, home_id, away_id, match_date, m)
        row.update(h2h_player)

        # ---- Lineup percentile features (Fase A) ----
        home_pct = _query_lineup_percentile_features(conn, home_id, match_date, fix["league_id"], season)
        away_pct = _query_lineup_percentile_features(conn, away_id, match_date, fix["league_id"], season)
        for key, val in home_pct.items():
            row[f"home_{key}"] = val
        for key, val in away_pct.items():
            row[f"away_{key}"] = val

        # ---- Combined & diff — corners ----
        ck_h = row.get(f"home_roll_corner_kicks_last{n}")
        ck_a = row.get(f"away_roll_corner_kicks_last{n}")
        if ck_h is not None and ck_a is not None and not pd.isna(ck_h) and not pd.isna(ck_a):
            row["combined_corners"] = ck_h + ck_a
        else:
            row["combined_corners"] = np.nan

        # ---- Balance features (señal para empates) ----
        xg_h = row.get(f"home_roll_xg_for_last{n}")
        xg_a = row.get(f"away_roll_xg_for_last{n}")
        if xg_h is not None and xg_a is not None and not pd.isna(xg_h) and not pd.isna(xg_a):
            row["xg_balance"] = 1.0 - abs(xg_h - xg_a) / (xg_h + xg_a + 1e-6)
        else:
            row["xg_balance"] = np.nan

        ppg_h = row.get("home_season_ppg")
        ppg_a = row.get("away_season_ppg")
        if ppg_h is not None and ppg_a is not None and not pd.isna(ppg_h) and not pd.isna(ppg_a):
            ppg_max = max(ppg_h, ppg_a)
            ppg_min = min(ppg_h, ppg_a)
            row["ppg_balance"] = ppg_min / (ppg_max + 1e-6)
        else:
            row["ppg_balance"] = np.nan

        # ---- Diff features — team stats ----
        diff_pairs = [
            (f"home_roll_goals_for_last{n}",       f"away_roll_goals_for_last{n}",       "diff_goals_for"),
            (f"home_roll_goals_against_last{n}",   f"away_roll_goals_against_last{n}",   "diff_goals_against"),
            (f"home_roll_won_last{n}",             f"away_roll_won_last{n}",             "diff_wins"),
            (f"home_roll_shots_on_goal_last{n}",   f"away_roll_shots_on_goal_last{n}",   "diff_shots_on_goal"),
            (f"home_roll_possession_last{n}",      f"away_roll_possession_last{n}",      "diff_possession"),
            (f"home_roll_passes_pct_last{n}",      f"away_roll_passes_pct_last{n}",      "diff_passes_pct"),
            (f"home_roll_xg_for_last{n}",          f"away_roll_xg_for_last{n}",          "diff_xg"),
            (f"home_roll_xg_against_last{n}",      f"away_roll_xg_against_last{n}",      "diff_xga"),
            ("home_season_ppg",                    "away_season_ppg",                    "diff_season_ppg"),
            ("home_season_gfpg",                   "away_season_gfpg",                   "diff_season_gfpg"),
            ("home_season_gapg",                   "away_season_gapg",                   "diff_season_gapg"),
            (f"home_roll_corner_kicks_last{n}",    f"away_roll_corner_kicks_last{n}",    "diff_corners"),
            (f"home_roll_corners_against_last{n}", f"away_roll_corners_against_last{n}", "diff_corners_against"),
            (f"home_roll_corner_ratio_last{n}",    f"away_roll_corner_ratio_last{n}",    "corner_dominance_diff"),
        ]
        # ---- Diff features — player stats ----
        player_diff_pairs = [
            (f"home_roll_player_avg_rating_last{n}",           f"away_roll_player_avg_rating_last{n}",           "diff_avg_rating"),
            (f"home_roll_player_goals_pstarted_last{n}",       f"away_roll_player_goals_pstarted_last{n}",       "diff_goals_pstarted"),
            (f"home_roll_player_key_passes_pstarted_last{n}",  f"away_roll_player_key_passes_pstarted_last{n}",  "diff_key_passes"),
            (f"home_roll_player_def_actions_pstarted_last{n}", f"away_roll_player_def_actions_pstarted_last{n}", "diff_def_actions"),
            (f"home_roll_player_avg_rating_f_last{n}",         f"away_roll_player_avg_rating_f_last{n}",         "diff_att_rating"),
            (f"home_roll_player_avg_rating_d_last{n}",         f"away_roll_player_avg_rating_d_last{n}",         "diff_def_rating"),
            (f"home_roll_player_gk_avg_rating_last{n}",        f"away_roll_player_gk_avg_rating_last{n}",        "diff_gk_rating"),
            (f"home_roll_player_gk_save_pct_last{n}",          f"away_roll_player_gk_save_pct_last{n}",          "diff_gk_save_pct"),
            ("h2h_home_avg_rating",                             "h2h_away_avg_rating",                            "h2h_diff_avg_rating"),
        ]

        ema_diff_pairs = [
            (f"home_ema_goals_for_span{n}",     f"away_ema_goals_for_span{n}",     "diff_ema_goals_for"),
            (f"home_ema_goals_against_span{n}", f"away_ema_goals_against_span{n}", "diff_ema_goals_against"),
            (f"home_ema_won_span{n}",           f"away_ema_won_span{n}",           "diff_ema_won"),
            (f"home_ema_scored_span{n}",        f"away_ema_scored_span{n}",        "diff_ema_scored"),
            (f"home_ema_clean_sheet_span{n}",   f"away_ema_clean_sheet_span{n}",   "diff_ema_clean_sheet"),
            (f"home_ema_xg_for_span{n}",        f"away_ema_xg_for_span{n}",        "diff_ema_xg"),
        ]

        pct_diff_pairs = [
            ("home_avg_att_goal_pct",       "away_avg_att_goal_pct",       "diff_att_goal_pct"),
            ("home_top_attacker_goal_pct",  "away_top_attacker_goal_pct",  "diff_top_attacker_pct"),
            ("home_avg_def_pct",            "away_avg_def_pct",            "diff_def_pct"),
            ("home_avg_starter_rating_pct", "away_avg_starter_rating_pct", "diff_starter_rating_pct"),
        ]

        for col_h, col_a, name in diff_pairs + player_diff_pairs + ema_diff_pairs + pct_diff_pairs:
            h_val = row.get(col_h)
            a_val = row.get(col_a)
            if (h_val is not None and a_val is not None
                    and not pd.isna(h_val) and not pd.isna(a_val)):
                row[name] = h_val - a_val
            else:
                row[name] = np.nan

    finally:
        conn.close()

    df = pd.DataFrame([row])
    return df, warnings, fix


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------

def _run_compute_player_percentiles(season: str | None = None) -> None:
    """Calcula percentiles de jugadores desde player_match_stats en segundo plano."""
    try:
        n = _compute_pp.run(season=season)
        print(f"[compute-player-percentiles] {n} filas insertadas/actualizadas")
    except Exception as exc:
        print(f"[compute-player-percentiles] ERROR: {exc}")


def _run_incremental_percentiles() -> None:
    """Ejecuta compute_percentiles --incremental en segundo plano."""
    try:
        conn = _compute_pct.get_connection()
        _compute_pct.ensure_table(conn)
        processed = _compute_pct.load_processed_dates(conn)
        fixtures = _compute_pct.load_fixtures(conn)
        player_stats = _compute_pct.load_player_stats(conn)
        pct_df = _compute_pct.compute_temporal_percentiles(fixtures, player_stats, processed)
        n = _compute_pct.upsert_to_db(conn, pct_df)
        conn.close()
        print(f"[refresh-percentiles] {n} filas insertadas/actualizadas")
    except Exception as exc:
        print(f"[refresh-percentiles] ERROR: {exc}")


@app.get("/health")
def health():
    return {
        "status":        "ok",
        "models_loaded": list(MODELS.keys()),
    }


@app.post("/compute-player-percentiles", status_code=202)
def compute_player_percentiles(background_tasks: BackgroundTasks, season: str | None = None):
    background_tasks.add_task(_run_compute_player_percentiles, season)
    return {"status": "accepted", "message": "Cálculo de percentiles de jugadores iniciado en segundo plano"}


@app.post("/refresh-percentiles", status_code=202)
def refresh_percentiles(background_tasks: BackgroundTasks):
    background_tasks.add_task(_run_incremental_percentiles)
    return {"status": "accepted", "message": "Actualización de percentiles iniciada en segundo plano"}


@app.post("/predict")
def predict(req: PredictRequest):
    # Inferir ventana lookback desde el modelo 1x2 (referencia)
    n = _infer_lookback(MODELS["lgbm_1x2"]["metadata"]["features"])
    m = 5  # H2H lookback

    df_all, warnings, fix = build_feature_row(req.fixture_id, n, m)

    def _proba(model_name: str) -> np.ndarray:
        meta = MODELS[model_name]
        df_m = _align_features(df_all.copy(), meta["metadata"]["features"])
        return meta["model"].predict_proba(df_m)[0]

    # ── 1X2 ──────────────────────────────────────────────────────────────────
    probs_1x2  = _proba("lgbm_1x2")
    pred_1x2   = int(np.argmax(probs_1x2))
    label_map  = {0: "home_win", 1: "draw", 2: "away_win"}
    sorted_p   = sorted(probs_1x2, reverse=True)
    confidence = round(float(sorted_p[0] - sorted_p[1]), 4)

    # ── BTTS ─────────────────────────────────────────────────────────────────
    probs_btts = _proba("lgbm_btts")

    # ── Goles totales (Poisson) — deriva over_05/15/25/35 con monotonía garantizada
    goals_meta = MODELS["lgbm_goals_lambda"]
    df_goals   = _align_features(df_all.copy(), goals_meta["metadata"]["features"])
    lam_goals  = float(goals_meta["model"].predict_lambda(df_goals)[0])
    # k=0 → P(X>0.5), k=1 → P(X>1.5), k=2 → P(X>2.5), k=3 → P(X>3.5)
    _gp = {k: round(float(1.0 - scipy_poisson.cdf(k, lam_goals)), 4) for k in range(4)}

    # ── Córners (Poisson) ─────────────────────────────────────────────────────
    corners_meta = MODELS["lgbm_corners_lambda"]
    df_corners   = _align_features(df_all.copy(), corners_meta["metadata"]["features"])
    lam_corners  = float(corners_meta["model"].predict_lambda(df_corners)[0])
    corner_probs = {
        f"over_{t}": round(float(1.0 - scipy_poisson.cdf(t, lam_corners)), 4)
        for t in range(3, 11)
    }

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
        "goals": {
            "expected_total": round(lam_goals, 2),
            "over_05": {"over": _gp[0], "under": round(1 - _gp[0], 4), "predicted": "over" if _gp[0] >= 0.5 else "under"},
            "over_15": {"over": _gp[1], "under": round(1 - _gp[1], 4), "predicted": "over" if _gp[1] >= 0.5 else "under"},
            "over_25": {"over": _gp[2], "under": round(1 - _gp[2], 4), "predicted": "over" if _gp[2] >= 0.5 else "under"},
            "over_35": {"over": _gp[3], "under": round(1 - _gp[3], 4), "predicted": "over" if _gp[3] >= 0.5 else "under"},
        },
        "btts": {
            "yes":       round(float(probs_btts[1]), 4),
            "no":        round(float(probs_btts[0]), 4),
            "predicted": "yes" if probs_btts[1] >= 0.5 else "no",
        },
        "corners": {
            "expected_total": round(lam_corners, 2),
            **corner_probs,
        },
        "warnings": warnings,
    }
