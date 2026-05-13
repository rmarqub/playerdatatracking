"""
Feature engineering para el modelo predictivo de partidos.

Genera un dataset de entrenamiento a partir de PostgreSQL con:
  - Rolling stats de los últimos N partidos por equipo (forma reciente)
  - Split home/away performance
  - Días de descanso entre partidos
  - Estadísticas H2H de los últimos M enfrentamientos
  - Rolling stats de jugadores titulares por equipo (rating, goles, pases clave, acciones def.)
  - H2H de jugadores: rendimiento de los titulares en los últimos M enfrentamientos directos
  - Percentiles temporales de jugadores (calculados al vuelo, sin leakage)
  - Variables objetivo: result (1X2), over25, btts, total_goals

Nota: Los percentiles temporales se calculan directamente en memoria desde fixture_player_stats.
Nota 2: La tabla player_season_percentiles existe para predicciones contextuales del backend
        (GetContextualMatchPrediction.java), pero NO se usa en el entrenamiento del modelo.

Uso:
    python feature_engineering.py
    python feature_engineering.py --lookback 7 --h2h 8 --output dataset.parquet
"""

import argparse
import sys
from pathlib import Path
from typing import Optional

import numpy as np
import pandas as pd
import psycopg2
from psycopg2.extras import RealDictCursor

# ---------------------------------------------------------------------------
# Config — ajusta estos valores a tu entorno local
# ---------------------------------------------------------------------------
DB_CONFIG = {
    "host": "localhost",
    "port": 5432,
    "dbname": "playerdata",
    "user": "postgres",
    "password": "admin",
}

DEFAULT_LOOKBACK = 5   # ventana rolling de últimos N partidos
DEFAULT_H2H      = 5   # últimos M enfrentamientos directos
OUTPUT_FILE      = Path(__file__).parent / "training_data.parquet"


# ---------------------------------------------------------------------------
# Carga de datos desde PostgreSQL
# ---------------------------------------------------------------------------

def get_connection():
    return psycopg2.connect(**DB_CONFIG, cursor_factory=RealDictCursor)


def load_fixtures(conn) -> pd.DataFrame:
    query = """
        SELECT
            f.id,
            f.league_id,
            f.league_name,
            f.season,
            f.match_date,
            f.home_team_id,
            f.home_team_name,
            f.away_team_id,
            f.away_team_name,
            f.goals_home,
            f.goals_away
        FROM fixture f
        WHERE f.status_short = 'FT'
          AND f.goals_home IS NOT NULL
          AND f.goals_away IS NOT NULL
        ORDER BY f.match_date ASC
    """
    with conn.cursor() as cur:
        cur.execute(query)
        rows = cur.fetchall()
    df = pd.DataFrame([dict(r) for r in rows])
    df["match_date"] = pd.to_datetime(df["match_date"], utc=True)
    return df


def load_team_stats(conn) -> pd.DataFrame:
    query = """
        SELECT
            ts.fixture_id,
            ts.team_id,
            ts.shots_on_goal,
            ts.shots_total,
            ts.ball_possession,
            ts.passes_pct,
            ts.corner_kicks,
            ts.fouls,
            ts.yellow_cards,
            ts.red_cards,
            ts.expected_goals,
            ts.goalkeeper_saves,
            ts.shots_inside_box
        FROM fixture_team_stats ts
        JOIN fixture f ON f.id = ts.fixture_id
        WHERE f.status_short = 'FT'
    """
    with conn.cursor() as cur:
        cur.execute(query)
        rows = cur.fetchall()
    return pd.DataFrame([dict(r) for r in rows])


def load_player_stats(conn) -> pd.DataFrame:
    """
    Carga stats individuales de jugadores para todos los partidos FT.
    Incluye solo las columnas necesarias para el feature engineering.
    """
    query = """
        SELECT
            ps.fixture_id,
            ps.team_id,
            ps.player_id,
            ps.position,
            ps.substitute,
            ps.minutes_played,
            ps.rating::float         AS rating,
            ps.goals_scored,
            ps.assists,
            ps.shots_on,
            ps.passes_key,
            ps.tackles_total,
            ps.interceptions,
            ps.duels_total,
            ps.duels_won,
            ps.saves,
            ps.goals_conceded,
            f.match_date,
            f.home_team_id,
            f.away_team_id
        FROM fixture_player_stats ps
        JOIN fixture f ON f.id = ps.fixture_id
        WHERE f.status_short = 'FT'
        ORDER BY f.match_date ASC
    """
    with conn.cursor() as cur:
        cur.execute(query)
        rows = cur.fetchall()
    if not rows:
        return pd.DataFrame()
    df = pd.DataFrame([dict(r) for r in rows])
    df["match_date"] = pd.to_datetime(df["match_date"], utc=True)
    df["rating"] = pd.to_numeric(df["rating"], errors="coerce")
    return df


# ---------------------------------------------------------------------------
# Construcción del historial por equipo — team stats
# ---------------------------------------------------------------------------

def build_team_history(fixtures: pd.DataFrame, team_stats: pd.DataFrame) -> pd.DataFrame:
    """
    Genera una fila por (equipo, partido) con perspectiva de goles a favor/en contra.
    Combina fixtures y team_stats para tener métricas de rendimiento.
    """
    ts_idx = (
        team_stats
        .set_index(["fixture_id", "team_id"])
        .to_dict("index")
    )

    records = []
    for f in fixtures.itertuples(index=False):
        for venue, team_id, rival_id, g_for, g_against in [
            ("H", f.home_team_id, f.away_team_id, f.goals_home, f.goals_away),
            ("A", f.away_team_id, f.home_team_id, f.goals_away, f.goals_home),
        ]:
            ts       = ts_idx.get((f.id, team_id), {})
            rival_ts = ts_idx.get((f.id, rival_id), {})
            records.append({
                "fixture_id":    f.id,
                "match_date":    f.match_date,
                "team_id":       team_id,
                "venue":         venue,
                "goals_for":     g_for,
                "goals_against": g_against,
                "won":           1 if g_for > g_against else 0,
                "drew":          1 if g_for == g_against else 0,
                "lost":          1 if g_for < g_against else 0,
                "scored":        1 if g_for > 0 else 0,
                "clean_sheet":   1 if g_against == 0 else 0,
                "xg_for":        ts.get("expected_goals"),
                "xg_against":    rival_ts.get("expected_goals"),
                "shots_on_goal": ts.get("shots_on_goal"),
                "shots_total":   ts.get("shots_total"),
                "possession":    ts.get("ball_possession"),
                "passes_pct":    ts.get("passes_pct"),
                "corner_kicks":  ts.get("corner_kicks"),
                "saves":         ts.get("goalkeeper_saves"),
                # Nuevas columnas para eficiencia y disciplina
                "shots_inside_box": ts.get("shots_inside_box"),
                "fouls":            ts.get("fouls"),
                "yellow_cards":     ts.get("yellow_cards"),
                "corners_against":  rival_ts.get("corner_kicks"),
            })

    df = pd.DataFrame(records)

    # Ratios derivados — calculados vectorizados para evitar división en el bucle
    shots_total_safe = df["shots_total"].replace(0, np.nan)
    df["shooting_accuracy"]     = df["shots_on_goal"] / shots_total_safe
    df["shots_inside_box_rate"] = df["shots_inside_box"] / shots_total_safe
    df["corner_ratio"]          = df["corner_kicks"] / (df["corner_kicks"] + df["corners_against"] + 1e-6)
    df["fouls_per_shot"]        = df["fouls"] / shots_total_safe

    df = df.sort_values(["team_id", "match_date"]).reset_index(drop=True)
    return df


# ---------------------------------------------------------------------------
# Construcción del historial por equipo — player stats
# ---------------------------------------------------------------------------

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
    "lineup_continuity",
]


def _compute_lineup_continuity(starters: pd.DataFrame) -> pd.DataFrame:
    """
    Para cada (team_id, fixture_id) calcula qué fracción de los titulares repite
    respecto al partido anterior del mismo equipo.
    lineup_continuity = |prev_starters ∩ curr_starters| / max(|prev|, 11)
    Requiere columnas: fixture_id, team_id, player_id, match_date.
    """
    if starters.empty:
        return pd.DataFrame(columns=["fixture_id", "team_id", "lineup_continuity"])

    parts = []
    for team_id, grp in starters.groupby("team_id", sort=False):
        fixture_sets = (
            grp.sort_values("match_date")
            .groupby("fixture_id", sort=False)
            .agg(starters_set=("player_id", set), match_date=("match_date", "first"))
            .reset_index()
            .sort_values("match_date")
            .reset_index(drop=True)
        )

        conts = [np.nan]
        for i in range(1, len(fixture_sets)):
            prev = fixture_sets.at[i - 1, "starters_set"]
            curr = fixture_sets.at[i, "starters_set"]
            conts.append(len(prev & curr) / max(len(prev), 11) if prev else np.nan)

        fixture_sets["lineup_continuity"] = conts
        fixture_sets["team_id"] = team_id
        parts.append(fixture_sets[["fixture_id", "team_id", "lineup_continuity"]])

    return pd.concat(parts, ignore_index=True) if parts else pd.DataFrame(
        columns=["fixture_id", "team_id", "lineup_continuity"]
    )


def build_team_player_history(player_stats: pd.DataFrame, fixtures: pd.DataFrame) -> pd.DataFrame:
    """
    Agrega stats de titulares (substitute=False) por (equipo, partido).
    Devuelve una fila por (fixture_id, team_id) con métricas de calidad del once:
      - Overall: avg_rating, goals_pstarted, key_passes_pstarted, def_actions_pstarted, duel_win_pct
      - Portero: gk_avg_rating, gk_save_pct
      - Por posición: avg_rating_d/m/f
      - Goleador: max_scorer_goals, goals_concentration
      - Estabilidad: lineup_continuity (overlap con partido anterior)
    """
    if player_stats.empty:
        return pd.DataFrame()

    starters = player_stats[player_stats["substitute"] == False].copy()

    # ---- Outfield (no portero): acciones defensivas ----
    outfield = starters[starters["position"] != "G"].copy()
    outfield["def_actions"] = outfield["tackles_total"].fillna(0) + outfield["interceptions"].fillna(0)
    outfield_agg = outfield.groupby(["fixture_id", "team_id"]).agg(
        sum_def_actions=("def_actions", "sum"),
        n_outfield=("player_id", "count"),
    ).reset_index()

    # ---- Todos los titulares: métricas globales ----
    starter_agg = starters.groupby(["fixture_id", "team_id"]).agg(
        n_starters=("player_id", "count"),
        avg_rating=("rating", "mean"),
        sum_goals=("goals_scored", "sum"),
        sum_key_passes=("passes_key", "sum"),
        sum_duels_won=("duels_won", "sum"),
        sum_duels_total=("duels_total", "sum"),
        max_scorer_goals=("goals_scored", "max"),
    ).reset_index()

    # ---- Portero ----
    gk = starters[starters["position"] == "G"]
    if not gk.empty:
        gk_agg = gk.groupby(["fixture_id", "team_id"]).agg(
            gk_avg_rating=("rating", "mean"),
            gk_saves=("saves", "sum"),
            gk_goals_conceded=("goals_conceded", "sum"),
        ).reset_index()
        denom = gk_agg["gk_saves"] + gk_agg["gk_goals_conceded"]
        gk_agg["gk_save_pct"] = gk_agg["gk_saves"] / denom.replace(0, np.nan)
    else:
        gk_agg = pd.DataFrame(columns=["fixture_id", "team_id", "gk_avg_rating", "gk_save_pct"])

    # ---- Rating por posición ----
    def _pos_rating_agg(df_pos: pd.DataFrame, col_name: str) -> pd.DataFrame:
        if df_pos.empty:
            return pd.DataFrame(columns=["fixture_id", "team_id", col_name])
        return (
            df_pos.groupby(["fixture_id", "team_id"])
            .agg(**{col_name: ("rating", "mean")})
            .reset_index()
        )

    def_agg = _pos_rating_agg(starters[starters["position"] == "D"], "avg_rating_d")
    mid_agg = _pos_rating_agg(starters[starters["position"] == "M"], "avg_rating_m")
    att_agg = _pos_rating_agg(starters[starters["position"] == "F"], "avg_rating_f")

    # ---- Continuidad de alineación ----
    lineup_cont = _compute_lineup_continuity(starters)

    # ---- Merge ----
    agg = starter_agg.merge(outfield_agg, on=["fixture_id", "team_id"], how="left")
    for df_part in [gk_agg, def_agg, mid_agg, att_agg, lineup_cont]:
        if not df_part.empty:
            agg = agg.merge(df_part, on=["fixture_id", "team_id"], how="left")

    # ---- Ratios derivados ----
    agg["goals_pstarted"]       = agg["sum_goals"] / agg["n_starters"]
    agg["key_passes_pstarted"]  = agg["sum_key_passes"] / agg["n_starters"]
    agg["def_actions_pstarted"] = agg["sum_def_actions"] / agg["n_outfield"].replace(0, np.nan)
    agg["duel_win_pct"]         = agg["sum_duels_won"] / agg["sum_duels_total"].replace(0, np.nan)
    agg["goals_concentration"]  = agg["max_scorer_goals"] / agg["sum_goals"].replace(0, np.nan)

    # ---- Datos insuficientes → NaN ----
    mask = agg["n_starters"] < 6
    for col in PLAYER_ROLL_COLS:
        if col in agg.columns:
            agg.loc[mask, col] = np.nan

    # ---- Match date y orden ----
    fixture_dates = fixtures[["id", "match_date"]].rename(columns={"id": "fixture_id"})
    result = agg.merge(fixture_dates, on="fixture_id", how="left")
    result = result.sort_values(["team_id", "match_date"]).reset_index(drop=True)

    keep_cols = ["fixture_id", "team_id", "match_date"] + [c for c in PLAYER_ROLL_COLS if c in result.columns]
    return result[keep_cols]


# ---------------------------------------------------------------------------
# Rolling features — team stats
# ---------------------------------------------------------------------------

ROLL_COLS = [
    "goals_for", "goals_against", "won", "drew", "lost",
    "scored", "clean_sheet",
    "xg_for", "xg_against", "shots_on_goal", "shots_total",
    "possession", "passes_pct", "corner_kicks", "saves",
    # Nuevas columnas — Eficiencia, disciplina, corners
    "shooting_accuracy", "shots_inside_box_rate",
    "corners_against", "corner_ratio",
    "fouls_per_shot", "yellow_cards", "fouls",
]

# Columnas usadas para EMA (subconjunto de ROLL_COLS — las más informativas para recencia)
EMA_COLS = ["goals_for", "goals_against", "won", "scored", "clean_sheet", "xg_for", "xg_against", "shots_on_goal"]


def _rolling_mean(series: pd.Series, n: int) -> pd.Series:
    """Shift(1) para que el partido actual no se incluya en su propio feature."""
    return series.shift(1).rolling(n, min_periods=1).mean()


def compute_rolling_features(history: pd.DataFrame, n: int) -> pd.DataFrame:
    """Rolling last-N stats por equipo. Devuelve columna por stat con prefijo roll_."""
    rolled_parts = []
    for team_id, grp in history.groupby("team_id", sort=False):
        grp = grp.sort_values("match_date")
        part = grp[["fixture_id", "team_id", "venue", "match_date"]].copy()
        for col in ROLL_COLS:
            part[f"roll_{col}_last{n}"] = _rolling_mean(grp[col].reset_index(drop=True), n).values
        rolled_parts.append(part)

    return pd.concat(rolled_parts, ignore_index=True)


def compute_rolling_home_away(history: pd.DataFrame, n: int) -> pd.DataFrame:
    """
    Rolling stats separando rendimiento en casa / fuera.
    Aporta información sobre si un equipo es mejor jugando de local.
    """
    parts = []
    for (team_id, venue), grp in history.groupby(["team_id", "venue"], sort=False):
        grp = grp.sort_values("match_date")
        part = grp[["fixture_id", "team_id", "venue"]].copy()
        for col in ["goals_for", "goals_against", "won", "xg_for", "shots_on_goal"]:
            part[f"roll_{col}_{venue.lower()}_last{n}"] = _rolling_mean(
                grp[col].reset_index(drop=True), n
            ).values
        parts.append(part)

    return pd.concat(parts, ignore_index=True)


def compute_days_rest(history: pd.DataFrame) -> pd.DataFrame:
    """Días desde el último partido jugado por cada equipo."""
    parts = []
    for team_id, grp in history.groupby("team_id", sort=False):
        grp = grp.sort_values("match_date").copy()
        grp["days_rest"] = grp["match_date"].diff().dt.days
        parts.append(grp[["fixture_id", "team_id", "days_rest"]])
    return pd.concat(parts, ignore_index=True)


def compute_season_form(fixtures: pd.DataFrame) -> pd.DataFrame:
    """
    Para cada equipo en cada partido calcula, con datos PREVIOS de esa misma temporada:
      - season_ppg   : puntos por partido (wins×3 + draws×1) / partidos jugados
      - season_gfpg  : goles a favor por partido
      - season_gapg  : goles en contra por partido
      - season_games : partidos jugados en la temporada hasta ese momento

    Reemplaza home_team_id/away_team_id como indicador de calidad del equipo.
    Captura la fuerza del equipo de forma dinámica y temporal, no estática.
    """
    records = []
    for f in fixtures.itertuples(index=False):
        for team_id, g_for, g_against in [
            (f.home_team_id, f.goals_home, f.goals_away),
            (f.away_team_id, f.goals_away, f.goals_home),
        ]:
            pts = 3 if g_for > g_against else (1 if g_for == g_against else 0)
            records.append({
                "fixture_id":   f.id,
                "team_id":      team_id,
                "season":       f.season,
                "match_date":   f.match_date,
                "points":       pts,
                "goals_for":    g_for,
                "goals_against": g_against,
            })

    df = pd.DataFrame(records).sort_values(["team_id", "season", "match_date"])

    parts = []
    for (team_id, season), grp in df.groupby(["team_id", "season"], sort=False):
        grp = grp.sort_values("match_date").copy()
        # shift(1): el partido actual no se incluye en su propio cómputo
        s_pts = grp["points"].shift(1)
        s_gf  = grp["goals_for"].shift(1)
        s_ga  = grp["goals_against"].shift(1)
        games = s_pts.notna().cumsum()

        grp["season_ppg"]   = s_pts.cumsum() / games.replace(0, np.nan)
        grp["season_gfpg"]  = s_gf.cumsum()  / games.replace(0, np.nan)
        grp["season_gapg"]  = s_ga.cumsum()  / games.replace(0, np.nan)
        grp["season_games"] = games

        parts.append(grp[["fixture_id", "team_id", "season_ppg", "season_gfpg",
                           "season_gapg", "season_games"]])

    return pd.concat(parts, ignore_index=True)


# ---------------------------------------------------------------------------
# Draw tendency features — propensión histórica a empates
# ---------------------------------------------------------------------------

def compute_season_draw_features(fixtures: pd.DataFrame) -> pd.DataFrame:
    """
    Para cada equipo en cada partido calcula, con datos PREVIOS de esa misma temporada:
      - season_draw_rate: fracción de partidos que terminaron en empate

    Señal clave para detectar equipos propensos a empatar. Usa shift(1) para evitar leakage.
    """
    records = []
    for f in fixtures.itertuples(index=False):
        for team_id, g_for, g_against in [
            (f.home_team_id, f.goals_home, f.goals_away),
            (f.away_team_id, f.goals_away, f.goals_home),
        ]:
            drew = 1 if g_for == g_against else 0
            records.append({
                "fixture_id": f.id,
                "team_id":    team_id,
                "season":     f.season,
                "match_date": f.match_date,
                "drew":       drew,
            })

    df = pd.DataFrame(records).sort_values(["team_id", "season", "match_date"])

    parts = []
    for (team_id, season), grp in df.groupby(["team_id", "season"], sort=False):
        grp = grp.sort_values("match_date").copy()
        s_drew = grp["drew"].shift(1)
        games  = s_drew.notna().cumsum()

        grp["season_draw_rate"] = s_drew.cumsum() / games.replace(0, np.nan)

        parts.append(grp[["fixture_id", "team_id", "season_draw_rate"]])

    return pd.concat(parts, ignore_index=True)


# ---------------------------------------------------------------------------
# Consistency features — predictabilidad de equipos (rolling std dev)
# ---------------------------------------------------------------------------

def compute_consistency_features(history: pd.DataFrame, n: int) -> pd.DataFrame:
    """
    Rolling std dev de goals_for y goals_against por equipo.
    Alta varianza → equipo impredecible → señal para el clasificador.

    Usa shift(1) igual que _rolling_mean() para evitar leakage.
    Genera: roll_std_goals_for_lastN, roll_std_goals_against_lastN
    """
    parts = []
    for team_id, grp in history.groupby("team_id", sort=False):
        grp = grp.sort_values("match_date")
        part = grp[["fixture_id", "team_id", "venue", "match_date"]].copy()

        for col in ["goals_for", "goals_against"]:
            shifted = grp[col].reset_index(drop=True).shift(1)
            part[f"roll_std_{col}_last{n}"] = (
                shifted.rolling(n, min_periods=2).std().values
            )

        parts.append(part)

    return pd.concat(parts, ignore_index=True)


# ---------------------------------------------------------------------------
# EMA features (recencia — alternativa a rolling media simple)
# ---------------------------------------------------------------------------

def compute_ema_features(history: pd.DataFrame, span: int) -> pd.DataFrame:
    """
    Exponential moving average sobre las stats del historial.
    Pondera el partido más reciente más que el de hace N jornadas.
    span es el mismo que el lookback para que sea comparable con el rolling simple.
    """
    parts = []
    for team_id, grp in history.groupby("team_id", sort=False):
        grp = grp.sort_values("match_date")
        part = grp[["fixture_id", "team_id", "venue", "match_date"]].copy()
        for col in EMA_COLS:
            if col in grp.columns:
                shifted = grp[col].shift(1)
                part[f"ema_{col}_span{span}"] = (
                    shifted.ewm(span=span, min_periods=1, adjust=False).mean().values
                )
        parts.append(part)
    return pd.concat(parts, ignore_index=True)


# ---------------------------------------------------------------------------
# League base rates (reemplaza league_id categórico con señales interpretables)
# ---------------------------------------------------------------------------

def compute_league_rates(fixtures: pd.DataFrame) -> pd.DataFrame:
    """
    Para cada partido calcula, con todos los partidos PREVIOS de esa liga:
      - league_home_win_rate : tasa histórica de victorias locales en esa liga
      - league_avg_goals     : media de goles por partido en esa liga
      - league_over25_rate   : tasa histórica de over 2.5 en esa liga

    Más interpretable que el ID crudo y generaliza mejor a ligas nuevas.
    Requiere ≥ 10 partidos anteriores en la liga; si no, NaN.
    """
    fs = fixtures.sort_values("match_date").reset_index(drop=True).copy()
    fs["home_win_flag"]  = (fs["goals_home"] > fs["goals_away"]).astype(float)
    fs["total_goals_f"]  = fs["goals_home"] + fs["goals_away"]
    fs["over25_flag"]    = (fs["total_goals_f"] > 2.5).astype(float)

    parts = []
    for league_id, grp in fs.groupby("league_id", sort=False):
        grp = grp.sort_values("match_date").copy()
        count = grp["home_win_flag"].shift(1).expanding().count()

        grp["league_home_win_rate"] = grp["home_win_flag"].shift(1).expanding().mean()
        grp["league_avg_goals"]     = grp["total_goals_f"].shift(1).expanding().mean()
        grp["league_over25_rate"]   = grp["over25_flag"].shift(1).expanding().mean()

        # NaN si hay menos de 10 partidos previos en esa liga
        mask = count < 10
        for col in ["league_home_win_rate", "league_avg_goals", "league_over25_rate"]:
            grp.loc[mask, col] = np.nan

        parts.append(grp[["id", "league_home_win_rate", "league_avg_goals", "league_over25_rate"]])

    df = pd.concat(parts, ignore_index=True).rename(columns={"id": "fixture_id"})
    return df


# ---------------------------------------------------------------------------
# Rolling features — player stats
# ---------------------------------------------------------------------------

def compute_rolling_player_features(player_history: pd.DataFrame, n: int) -> pd.DataFrame:
    """
    Rolling last-N sobre las métricas agregadas de titulares por equipo.
    Produce: roll_player_{col}_last{n} por (fixture_id, team_id).
    """
    if player_history.empty:
        return pd.DataFrame()

    parts = []
    for team_id, grp in player_history.groupby("team_id", sort=False):
        grp = grp.sort_values("match_date").copy()
        part = grp[["fixture_id", "team_id", "match_date"]].copy()
        for col in PLAYER_ROLL_COLS:
            if col in grp.columns:
                part[f"roll_player_{col}_last{n}"] = _rolling_mean(
                    grp[col].reset_index(drop=True), n
                ).values
        parts.append(part)

    if not parts:
        return pd.DataFrame()
    return pd.concat(parts, ignore_index=True)


# ---------------------------------------------------------------------------
# H2H features — team stats
# ---------------------------------------------------------------------------

def compute_h2h_features(fixtures: pd.DataFrame, m: int) -> pd.DataFrame:
    """
    Para cada partido, busca los últimos M enfrentamientos entre los mismos
    dos equipos (en cualquier sentido) y calcula ratios de resultado y media de goles.
    """
    fixtures_sorted = fixtures.sort_values("match_date").reset_index(drop=True)

    records = []
    for f in fixtures_sorted.itertuples(index=False):
        home, away, date = f.home_team_id, f.away_team_id, f.match_date

        mask_teams = (
            ((fixtures_sorted["home_team_id"] == home) & (fixtures_sorted["away_team_id"] == away)) |
            ((fixtures_sorted["home_team_id"] == away) & (fixtures_sorted["away_team_id"] == home))
        )
        past = fixtures_sorted[mask_teams & (fixtures_sorted["match_date"] < date)].tail(m)

        if past.empty:
            records.append({
                "fixture_id":      f.id,
                "h2h_home_wins":   np.nan,
                "h2h_draws":       np.nan,
                "h2h_away_wins":   np.nan,
                "h2h_avg_goals":   np.nan,
                "h2h_count":       0,
            })
            continue

        n_matches = len(past)
        home_wins = sum(
            (r.home_team_id == home and r.goals_home > r.goals_away) or
            (r.away_team_id == home and r.goals_away > r.goals_home)
            for r in past.itertuples(index=False)
        )
        draws = sum(r.goals_home == r.goals_away for r in past.itertuples(index=False))
        away_wins = n_matches - home_wins - draws
        avg_goals = (past["goals_home"] + past["goals_away"]).mean()

        records.append({
            "fixture_id":    f.id,
            "h2h_home_wins": home_wins / n_matches,
            "h2h_draws":     draws / n_matches,
            "h2h_away_wins": away_wins / n_matches,
            "h2h_avg_goals": avg_goals,
            "h2h_count":     n_matches,
        })

    return pd.DataFrame(records)


# ---------------------------------------------------------------------------
# H2H features — player stats
# ---------------------------------------------------------------------------

def compute_h2h_player_features(
    player_stats: pd.DataFrame,
    fixtures: pd.DataFrame,
    m: int,
) -> pd.DataFrame:
    """
    Para cada partido, calcula el rendimiento histórico de los titulares de cada equipo
    en los últimos M encuentros directos contra ese rival concreto.
    Produce: h2h_home_avg_rating, h2h_away_avg_rating, h2h_home_goals_pstarted, h2h_away_goals_pstarted.
    """
    empty_row = {
        "h2h_home_avg_rating":     np.nan,
        "h2h_away_avg_rating":     np.nan,
        "h2h_home_goals_pstarted": np.nan,
        "h2h_away_goals_pstarted": np.nan,
    }

    if player_stats.empty:
        records = [{"fixture_id": fid, **empty_row} for fid in fixtures["id"]]
        return pd.DataFrame(records)

    fixtures_sorted = fixtures.sort_values("match_date").reset_index(drop=True)
    # Índice rápido: fixture_id → player stats de ese partido
    ps_by_fixture = player_stats[player_stats["substitute"] == False].groupby("fixture_id")

    records = []
    for f in fixtures_sorted.itertuples(index=False):
        home, away, date = f.home_team_id, f.away_team_id, f.match_date

        mask = (
            ((fixtures_sorted["home_team_id"] == home) & (fixtures_sorted["away_team_id"] == away)) |
            ((fixtures_sorted["home_team_id"] == away) & (fixtures_sorted["away_team_id"] == home))
        )
        past_ids = (
            fixtures_sorted[mask & (fixtures_sorted["match_date"] < date)]
            .tail(m)["id"]
            .tolist()
        )

        if not past_ids:
            records.append({"fixture_id": f.id, **empty_row})
            continue

        past_frames = [ps_by_fixture.get_group(fid) for fid in past_ids if fid in ps_by_fixture.groups]
        if not past_frames:
            records.append({"fixture_id": f.id, **empty_row})
            continue

        past_player = pd.concat(past_frames, ignore_index=True)
        home_ps = past_player[past_player["team_id"] == home]
        away_ps = past_player[past_player["team_id"] == away]

        def _safe_rating(df):
            return float(df["rating"].mean()) if not df.empty and df["rating"].notna().any() else np.nan

        def _safe_goals(df):
            if df.empty:
                return np.nan
            n = len(df)
            return float(df["goals_scored"].sum() / n) if n > 0 else np.nan

        records.append({
            "fixture_id":              f.id,
            "h2h_home_avg_rating":     _safe_rating(home_ps),
            "h2h_away_avg_rating":     _safe_rating(away_ps),
            "h2h_home_goals_pstarted": _safe_goals(home_ps),
            "h2h_away_goals_pstarted": _safe_goals(away_ps),
        })

    return pd.DataFrame(records)


# ---------------------------------------------------------------------------
# Percentiles de jugadores por liga/temporada — Desde BD (sin recálculo)
# ---------------------------------------------------------------------------

def compute_player_season_percentiles_temporal(
    player_stats: pd.DataFrame,
    fixtures: pd.DataFrame,
    min_minutes: int = 90,
) -> pd.DataFrame:
    """
    Calcula percentiles temporales en memoria (sin leakage).

    Para cada fixture F en fecha D, calcula el rango percentil de cada jugador
    usando SOLO partidos anteriores a D en la misma liga+temporada.
    Fixtures del mismo día usan el mismo snapshot (sus datos no están incluidos).

    Retorna: fixture_id, player_id, goals_p90_pct, kp_p90_pct, def_p90_pct, avg_rating_pct

    NOTA: Revertido a cálculo en memoria (en lugar de SQL directo) porque:
    - Versión anterior funcionaba sin problemas de memoria
    - Tabla player_season_percentiles requiere espacio temporal excesivo en PostgreSQL
    - Se mantiene player_season_percentiles SOLO para predicciones contextuales (backend)
    """
    if player_stats.empty:
        return pd.DataFrame()

    # player_stats ya trae match_date del JOIN en load_player_stats()
    # — solo necesitamos league_id y season de fixtures
    fix_meta = (
        fixtures[["id", "league_id", "season"]]
        .rename(columns={"id": "fixture_id"})
    )
    ps = player_stats.merge(fix_meta, on="fixture_id", how="left")
    ps = ps[ps["minutes_played"].fillna(0) > 0].copy()
    ps["match_date"]   = pd.to_datetime(ps["match_date"], utc=True)
    ps["rating_float"] = pd.to_numeric(ps["rating"], errors="coerce")

    results = []

    for (league_id, season), lg in ps.groupby(["league_id", "season"]):
        lg = lg.sort_values("match_date")

        # Agrupar por fecha — todos los fixtures del mismo día usan el mismo snapshot
        date_groups = (
            lg[["fixture_id", "match_date"]]
            .drop_duplicates("fixture_id")
            .groupby("match_date")["fixture_id"].apply(list)
            .reset_index()
            .sort_values("match_date")
        )

        cum: dict = {}  # player_id → acumulado de stats

        for _, drow in date_groups.iterrows():
            fdate = drow["match_date"]
            fids  = drow["fixture_id"]

            # snapshot ANTES de los fixtures de este día
            if cum:
                snap_rows = [
                    {
                        "player_id":     pid,
                        "total_minutes": d["minutes"],
                        "total_goals":   d["goals"],
                        "total_kp":      d["kp"],
                        "total_def":     d["tackles"] + d["int_"],
                        "avg_rating":    d["sum_r"] / d["n_r"] if d["n_r"] > 0 else np.nan,
                    }
                    for pid, d in cum.items()
                    if d["minutes"] >= min_minutes
                ]

                if len(snap_rows) >= 2:
                    snap = pd.DataFrame(snap_rows)
                    snap["goals_p90"] = snap["total_goals"] * 90.0 / snap["total_minutes"]
                    snap["kp_p90"]    = snap["total_kp"]    * 90.0 / snap["total_minutes"]
                    snap["def_p90"]   = snap["total_def"]   * 90.0 / snap["total_minutes"]
                    for raw, pct_col in [
                        ("goals_p90",  "goals_p90_pct"),
                        ("kp_p90",     "kp_p90_pct"),
                        ("def_p90",    "def_p90_pct"),
                        ("avg_rating", "avg_rating_pct"),
                    ]:
                        snap[pct_col] = snap[raw].rank(pct=True) * 100

                    pct_cols = snap[["player_id", "goals_p90_pct", "kp_p90_pct",
                                     "def_p90_pct", "avg_rating_pct"]]
                    for fid in fids:
                        fid_snap = pct_cols.copy()
                        fid_snap["fixture_id"] = fid
                        results.append(fid_snap)

            # actualizar acumulado con los datos de los fixtures de este día
            date_data = lg[lg["fixture_id"].isin(fids)]
            for _, prow in date_data.iterrows():
                pid = int(prow["player_id"])
                if pid not in cum:
                    cum[pid] = {"minutes": 0, "goals": 0, "kp": 0,
                                "tackles": 0, "int_": 0, "sum_r": 0.0, "n_r": 0}
                _si = lambda v: 0 if v is None or (isinstance(v, float) and np.isnan(v)) else int(v)
                cum[pid]["minutes"]  += _si(prow.get("minutes_played"))
                cum[pid]["goals"]    += _si(prow.get("goals_scored"))
                cum[pid]["kp"]       += _si(prow.get("passes_key"))
                cum[pid]["tackles"]  += _si(prow.get("tackles_total"))
                cum[pid]["int_"]     += _si(prow.get("interceptions"))
                r = prow.get("rating_float")
                if r is not None and not pd.isna(r):
                    cum[pid]["sum_r"] += float(r)
                    cum[pid]["n_r"]   += 1

    if not results:
        return pd.DataFrame()
    return pd.concat(results, ignore_index=True)


def compute_lineup_percentile_features(
    player_stats: pd.DataFrame,
    aligned_percentiles: pd.DataFrame,
) -> pd.DataFrame:
    """
    Para cada (fixture_id, team_id) agrega los percentiles temporales de los titulares.
    Los percentiles vienen del cálculo temporal en memoria (sin leakage).

    Features:
      avg_starter_rating_pct  — percentil medio de rating de todos los titulares
      avg_att_goal_pct        — percentil medio de goles/90 de F+M
      top_attacker_goal_pct   — máximo percentil de goles/90 (el mejor atacante)
      avg_att_kp_pct          — percentil medio de pases clave de F+M
      avg_def_pct             — percentil medio defensivo de D+G
    """
    if player_stats.empty or aligned_percentiles.empty:
        return pd.DataFrame()

    starters = player_stats[player_stats["substitute"] == False].copy()
    starters = starters.merge(aligned_percentiles, on=["fixture_id", "player_id"], how="left")

    att  = starters[starters["position"].isin(["F", "M"])]
    def_ = starters[starters["position"].isin(["D", "G"])]

    overall = starters.groupby(["fixture_id", "team_id"]).agg(
        avg_starter_rating_pct=("avg_rating_pct", "mean"),
        top_attacker_goal_pct =("goals_p90_pct",  "max"),
    ).reset_index()

    att_agg = att.groupby(["fixture_id", "team_id"]).agg(
        avg_att_goal_pct=("goals_p90_pct", "mean"),
        avg_att_kp_pct  =("kp_p90_pct",   "mean"),
    ).reset_index()

    def_agg = def_.groupby(["fixture_id", "team_id"]).agg(
        avg_def_pct=("def_p90_pct", "mean"),
    ).reset_index()

    result = overall.merge(att_agg, on=["fixture_id", "team_id"], how="left")
    result = result.merge(def_agg,  on=["fixture_id", "team_id"], how="left")
    return result


# ---------------------------------------------------------------------------
# Ensamblado del dataset final
# ---------------------------------------------------------------------------

def build_targets(fixtures: pd.DataFrame) -> pd.DataFrame:
    df = fixtures.copy()
    df["result"] = np.where(
        df["goals_home"] > df["goals_away"], 0,
        np.where(df["goals_home"] == df["goals_away"], 1, 2)
    )
    total = df["goals_home"] + df["goals_away"]
    df["over25"] = (total > 2.5).astype(int)
    df["over15"] = (total > 1.5).astype(int)
    df["over35"] = (total > 3.5).astype(int)
    df["btts"]   = ((df["goals_home"] > 0) & (df["goals_away"] > 0)).astype(int)
    df["total_goals"] = total
    return df


def _suffix_roll_cols(df: pd.DataFrame, prefix: str, exclude=("fixture_id", "team_id", "venue", "match_date")) -> pd.DataFrame:
    rename = {c: f"{prefix}_{c}" for c in df.columns if c not in exclude}
    return df.rename(columns=rename)


def assemble_dataset(
    fixtures: pd.DataFrame,
    rolling: pd.DataFrame,
    rolling_venue: pd.DataFrame,
    rest: pd.DataFrame,
    h2h: pd.DataFrame,
    season_form: pd.DataFrame,
    n: int,
    player_rolling: Optional[pd.DataFrame] = None,
    player_h2h: Optional[pd.DataFrame] = None,
    ema: Optional[pd.DataFrame] = None,
    league_rates: Optional[pd.DataFrame] = None,
    lineup_pct: Optional[pd.DataFrame] = None,
    draw_features: Optional[pd.DataFrame] = None,
    consistency: Optional[pd.DataFrame] = None,
) -> pd.DataFrame:
    base = build_targets(fixtures)

    def _fid(df: pd.DataFrame) -> pd.DataFrame:
        """Normaliza fixture_id → id para que todos los merges usen la misma clave."""
        return df.rename(columns={"fixture_id": "id"}) if "fixture_id" in df.columns else df

    # Rolling stats — separar home/away
    home_roll = rolling[rolling["venue"] == "H"].drop(columns=["venue", "match_date"])
    away_roll = rolling[rolling["venue"] == "A"].drop(columns=["venue", "match_date"])

    home_roll = _fid(home_roll).rename(columns={"team_id": "home_team_id"})
    home_roll = _suffix_roll_cols(home_roll, "home", exclude={"id", "home_team_id"})

    away_roll = _fid(away_roll).rename(columns={"team_id": "away_team_id"})
    away_roll = _suffix_roll_cols(away_roll, "away", exclude={"id", "away_team_id"})

    # Rolling venue-split — seleccionar solo las columnas del venue correspondiente
    h_cols = [c for c in rolling_venue.columns if f"_h_last{n}" in c]
    a_cols = [c for c in rolling_venue.columns if f"_a_last{n}" in c]

    home_venue = _fid(
        rolling_venue[rolling_venue["venue"] == "H"][["fixture_id", "team_id"] + h_cols].copy()
    ).rename(columns={"team_id": "home_team_id"})

    away_venue = _fid(
        rolling_venue[rolling_venue["venue"] == "A"][["fixture_id", "team_id"] + a_cols].copy()
    ).rename(columns={"team_id": "away_team_id"})

    # Days rest
    rest_norm = _fid(rest)
    home_rest = rest_norm.copy().rename(columns={"team_id": "home_team_id", "days_rest": "home_days_rest"})
    away_rest = rest_norm.copy().rename(columns={"team_id": "away_team_id", "days_rest": "away_days_rest"})

    # Season form
    sf = _fid(season_form)
    _sf_cols = {"season_ppg": "home_season_ppg", "season_gfpg": "home_season_gfpg",
                "season_gapg": "home_season_gapg", "season_games": "home_season_games"}
    home_sf = sf.copy().rename(columns={"team_id": "home_team_id", **_sf_cols})

    _sf_cols_a = {"season_ppg": "away_season_ppg", "season_gfpg": "away_season_gfpg",
                  "season_gapg": "away_season_gapg", "season_games": "away_season_games"}
    away_sf = sf.copy().rename(columns={"team_id": "away_team_id", **_sf_cols_a})

    df = (
        base
        .merge(home_roll,   on=["id", "home_team_id"], how="left")
        .merge(away_roll,   on=["id", "away_team_id"], how="left")
        .merge(home_venue,  on=["id", "home_team_id"], how="left")
        .merge(away_venue,  on=["id", "away_team_id"], how="left")
        .merge(home_rest,   on=["id", "home_team_id"], how="left")
        .merge(away_rest,   on=["id", "away_team_id"], how="left")
        .merge(_fid(h2h),   on="id",                   how="left")
        .merge(home_sf,     on=["id", "home_team_id"], how="left")
        .merge(away_sf,     on=["id", "away_team_id"], how="left")
    )

    # Draw tendency features — split home/away
    if draw_features is not None and not draw_features.empty:
        df_draw = _fid(draw_features)
        home_draw = df_draw.copy().rename(columns={
            "team_id": "home_team_id",
            "season_draw_rate": "home_season_draw_rate",
        })
        away_draw = df_draw.copy().rename(columns={
            "team_id": "away_team_id",
            "season_draw_rate": "away_season_draw_rate",
        })
        df = df.merge(home_draw, on=["id", "home_team_id"], how="left")
        df = df.merge(away_draw, on=["id", "away_team_id"], how="left")

    # Consistency features — rolling std dev split home/away por venue
    if consistency is not None and not consistency.empty:
        home_cons = consistency[consistency["venue"] == "H"].drop(columns=["venue", "match_date"])
        away_cons = consistency[consistency["venue"] == "A"].drop(columns=["venue", "match_date"])

        home_cons = _fid(home_cons).rename(columns={"team_id": "home_team_id"})
        home_cons = _suffix_roll_cols(home_cons, "home", exclude={"id", "home_team_id"})

        away_cons = _fid(away_cons).rename(columns={"team_id": "away_team_id"})
        away_cons = _suffix_roll_cols(away_cons, "away", exclude={"id", "away_team_id"})

        df = df.merge(home_cons, on=["id", "home_team_id"], how="left")
        df = df.merge(away_cons, on=["id", "away_team_id"], how="left")

    # Player rolling features — separar home/away a partir del fixture
    if player_rolling is not None and not player_rolling.empty:
        player_roll_cols = [c for c in player_rolling.columns if c.startswith("roll_player_")]

        fix_teams = fixtures[["id", "home_team_id", "away_team_id"]].rename(columns={"id": "fixture_id"})
        pr_with_venue = player_rolling.merge(fix_teams, on="fixture_id", how="left")
        pr_with_venue["venue"] = np.where(
            pr_with_venue["team_id"] == pr_with_venue["home_team_id"], "H", "A"
        )

        home_pr = (
            pr_with_venue[pr_with_venue["venue"] == "H"][["fixture_id", "team_id"] + player_roll_cols]
            .rename(columns={"fixture_id": "id", "team_id": "home_team_id"})
        )
        home_pr = _suffix_roll_cols(home_pr, "home", exclude={"id", "home_team_id"})

        away_pr = (
            pr_with_venue[pr_with_venue["venue"] == "A"][["fixture_id", "team_id"] + player_roll_cols]
            .rename(columns={"fixture_id": "id", "team_id": "away_team_id"})
        )
        away_pr = _suffix_roll_cols(away_pr, "away", exclude={"id", "away_team_id"})

        df = df.merge(home_pr, on=["id", "home_team_id"], how="left")
        df = df.merge(away_pr, on=["id", "away_team_id"], how="left")

    # H2H player features
    if player_h2h is not None and not player_h2h.empty:
        df = df.merge(_fid(player_h2h), on="id", how="left")

    # EMA features — mismo split home/away que rolling
    if ema is not None and not ema.empty:
        ema_cols = [c for c in ema.columns if c.startswith("ema_")]

        home_ema = ema[ema["venue"] == "H"].drop(columns=["venue", "match_date"])
        away_ema = ema[ema["venue"] == "A"].drop(columns=["venue", "match_date"])

        home_ema = _fid(home_ema).rename(columns={"team_id": "home_team_id"})
        home_ema = _suffix_roll_cols(home_ema, "home", exclude={"id", "home_team_id"})

        away_ema = _fid(away_ema).rename(columns={"team_id": "away_team_id"})
        away_ema = _suffix_roll_cols(away_ema, "away", exclude={"id", "away_team_id"})

        df = df.merge(home_ema, on=["id", "home_team_id"], how="left")
        df = df.merge(away_ema, on=["id", "away_team_id"], how="left")

    # League base rates
    if league_rates is not None and not league_rates.empty:
        df = df.merge(_fid(league_rates), on="id", how="left")

    # Percentiles de alineación — split home/away (igual que season_form)
    if lineup_pct is not None and not lineup_pct.empty:
        pct_cols = [c for c in lineup_pct.columns if c not in ("fixture_id", "team_id")]

        home_pct = (
            _fid(lineup_pct).rename(columns={"team_id": "home_team_id"})
        )
        home_pct = _suffix_roll_cols(home_pct, "home", exclude={"id", "home_team_id"})

        away_pct = (
            _fid(lineup_pct).rename(columns={"team_id": "away_team_id"})
        )
        away_pct = _suffix_roll_cols(away_pct, "away", exclude={"id", "away_team_id"})

        df = df.merge(home_pct, on=["id", "home_team_id"], how="left")
        df = df.merge(away_pct, on=["id", "away_team_id"], how="left")

    # Renombrar fixture.id → fixture_id
    df = df.rename(columns={"id": "fixture_id"})

    # Features de balance — señal para empates (cuán igualado está el partido)
    xg_h = f"home_roll_xg_for_last{n}"
    xg_a = f"away_roll_xg_for_last{n}"
    if xg_h in df.columns and xg_a in df.columns:
        xg_sum = df[xg_h] + df[xg_a] + 1e-6
        df["xg_balance"] = 1.0 - (df[xg_h] - df[xg_a]).abs() / xg_sum

    if "home_season_ppg" in df.columns and "away_season_ppg" in df.columns:
        ppg_max = df[["home_season_ppg", "away_season_ppg"]].max(axis=1)
        ppg_min = df[["home_season_ppg", "away_season_ppg"]].min(axis=1)
        df["ppg_balance"] = ppg_min / (ppg_max + 1e-6)

    # Draw tendency index — cuán parecidas son las tasas de empate de los dos equipos
    # Valores cercanos a 1 indican que ambos empatan con frecuencia similar → partido equilibrado
    if "home_season_draw_rate" in df.columns and "away_season_draw_rate" in df.columns:
        min_dr = df[["home_season_draw_rate", "away_season_draw_rate"]].min(axis=1)
        max_dr = df[["home_season_draw_rate", "away_season_draw_rate"]].max(axis=1)
        df["draw_tendency_index"] = min_dr / (max_dr + 1e-6)

    # Features diferenciales home-minus-away — team stats
    diff_pairs = [
        (f"home_roll_goals_for_last{n}",      f"away_roll_goals_for_last{n}",      "diff_goals_for"),
        (f"home_roll_goals_against_last{n}",  f"away_roll_goals_against_last{n}",  "diff_goals_against"),
        (f"home_roll_won_last{n}",            f"away_roll_won_last{n}",            "diff_wins"),
        (f"home_roll_shots_on_goal_last{n}",  f"away_roll_shots_on_goal_last{n}",  "diff_shots_on_goal"),
        (f"home_roll_possession_last{n}",     f"away_roll_possession_last{n}",     "diff_possession"),
        (f"home_roll_passes_pct_last{n}",     f"away_roll_passes_pct_last{n}",     "diff_passes_pct"),
        (f"home_roll_xg_for_last{n}",         f"away_roll_xg_for_last{n}",         "diff_xg"),
        (f"home_roll_xg_against_last{n}",     f"away_roll_xg_against_last{n}",     "diff_xga"),
        ("home_season_ppg",                   "away_season_ppg",                   "diff_season_ppg"),
        ("home_season_gfpg",                  "away_season_gfpg",                  "diff_season_gfpg"),
        ("home_season_gapg",                  "away_season_gapg",                  "diff_season_gapg"),
        # Nuevas diferenciales — corners, disciplina, eficiencia
        (f"home_roll_corner_kicks_last{n}",          f"away_roll_corner_kicks_last{n}",          "diff_corners"),
        (f"home_roll_corners_against_last{n}",       f"away_roll_corners_against_last{n}",       "diff_corners_against"),
        (f"home_roll_corner_ratio_last{n}",          f"away_roll_corner_ratio_last{n}",          "corner_dominance_diff"),
        (f"home_roll_yellow_cards_last{n}",          f"away_roll_yellow_cards_last{n}",          "diff_yellow_cards"),
        (f"home_roll_fouls_per_shot_last{n}",        f"away_roll_fouls_per_shot_last{n}",        "diff_fouls_per_shot"),
        (f"home_roll_shooting_accuracy_last{n}",     f"away_roll_shooting_accuracy_last{n}",     "diff_shooting_accuracy"),
        (f"home_roll_shots_inside_box_rate_last{n}", f"away_roll_shots_inside_box_rate_last{n}", "diff_shots_inside_box_rate"),
    ]
    # Features diferenciales — player stats
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

    # Features diferenciales — EMA
    ema_diff_pairs = [
        (f"home_ema_goals_for_span{n}",      f"away_ema_goals_for_span{n}",      "diff_ema_goals_for"),
        (f"home_ema_goals_against_span{n}",  f"away_ema_goals_against_span{n}",  "diff_ema_goals_against"),
        (f"home_ema_won_span{n}",            f"away_ema_won_span{n}",            "diff_ema_won"),
        (f"home_ema_scored_span{n}",         f"away_ema_scored_span{n}",         "diff_ema_scored"),
        (f"home_ema_clean_sheet_span{n}",    f"away_ema_clean_sheet_span{n}",    "diff_ema_clean_sheet"),
        (f"home_ema_xg_for_span{n}",         f"away_ema_xg_for_span{n}",         "diff_ema_xg"),
    ]

    # Features diferenciales — percentiles de alineación
    pct_diff_pairs = [
        ("home_avg_att_goal_pct",         "away_avg_att_goal_pct",         "diff_att_goal_pct"),
        ("home_top_attacker_goal_pct",    "away_top_attacker_goal_pct",    "diff_top_attacker_pct"),
        ("home_avg_def_pct",              "away_avg_def_pct",              "diff_def_pct"),
        ("home_avg_starter_rating_pct",   "away_avg_starter_rating_pct",   "diff_starter_rating_pct"),
    ]

    # Features diferenciales — draw tendency y consistency
    draw_cons_diff_pairs = []
    if "home_season_draw_rate" in df.columns and "away_season_draw_rate" in df.columns:
        draw_cons_diff_pairs.append(
            ("home_season_draw_rate", "away_season_draw_rate", "draw_tendency_diff")
        )
    for col in ["goals_for", "goals_against"]:
        h_col = f"home_roll_std_{col}_last{n}"
        a_col = f"away_roll_std_{col}_last{n}"
        if h_col in df.columns and a_col in df.columns:
            draw_cons_diff_pairs.append((h_col, a_col, f"consistency_diff_{col}"))

    for col_h, col_a, name in diff_pairs + player_diff_pairs + ema_diff_pairs + pct_diff_pairs + draw_cons_diff_pairs:
        if col_h in df.columns and col_a in df.columns:
            df[name] = df[col_h] - df[col_a]

    return df


# ---------------------------------------------------------------------------
# Diagnóstico de completitud
# ---------------------------------------------------------------------------

def print_diagnostics(fixtures: pd.DataFrame, dataset: pd.DataFrame, n: int,
                      aligned_pct: pd.DataFrame = None) -> None:
    print(f"\n{'='*55}")
    print(f"  Fixtures FT totales:           {len(fixtures):>7}")
    print(f"  Filas en dataset entrenamiento: {len(dataset):>7}")
    print(f"  Features generadas:            {len(dataset.columns):>7}")

    print(f"\n  Distribución targets:")
    print(f"    result  — 0(H):{(dataset['result']==0).mean():.1%}  "
          f"1(D):{(dataset['result']==1).mean():.1%}  "
          f"2(A):{(dataset['result']==2).mean():.1%}")
    print(f"    over25  — {dataset['over25'].mean():.1%} positivos")
    print(f"    btts    — {dataset['btts'].mean():.1%} positivos")

    # NUEVO: Cobertura de percentiles de jugadores
    if aligned_pct is not None and not aligned_pct.empty:
        pct_fixture_count = aligned_pct["fixture_id"].nunique()
        pct_coverage = pct_fixture_count / len(fixtures) * 100
        pct_player_count = len(aligned_pct)
        print(f"\n  📊 Cobertura de percentiles de jugadores:")
        print(f"    Fixtures con percentiles:  {pct_fixture_count:>7} / {len(fixtures)} ({pct_coverage:.1f}%)")
        print(f"    Total (fixture, player):   {pct_player_count:>7} alineamientos")
        if pct_coverage < 80:
            print(f"    ⚠ Cobertura baja: algunos fixtures no tienen percentiles")

    print(f"\n  Completitud de features clave (nulos):")
    checks = {
        f"home_roll_xg_for_last{n}":                      "xG for (home)",
        f"home_roll_shots_on_goal_last{n}":               "Shots on goal (home)",
        f"home_roll_possession_last{n}":                  "Possession (home)",
        f"home_roll_goals_for_last{n}":                   "Goals for (home)",
        "h2h_avg_goals":                                  "H2H avg goals",
        "home_days_rest":                                 "Days rest (home)",
        f"home_roll_player_avg_rating_last{n}":           "Player avg rating (home)",
        f"home_roll_player_goals_pstarted_last{n}":       "Player goals/starter (home)",
        f"home_roll_player_key_passes_pstarted_last{n}":  "Key passes/starter (home)",
        f"home_roll_player_def_actions_pstarted_last{n}": "Def actions/starter (home)",
        f"home_roll_player_duel_win_pct_last{n}":         "Duel win % (home)",
        "h2h_home_avg_rating":                            "H2H player rating (home)",
        f"home_roll_scored_last{n}":                      "Scoring rate (home)",
        f"home_roll_clean_sheet_last{n}":                 "Clean sheet rate (home)",
        f"home_ema_goals_for_span{n}":                    "EMA goals for (home)",
        "league_home_win_rate":                           "League home win rate",
        "league_avg_goals":                               "League avg goals",
        "home_avg_att_goal_pct":                          "Att goal percentile (home)",
        "home_top_attacker_goal_pct":                     "Top attacker percentile (home)",
        "home_avg_def_pct":                               "Def percentile (home)",
        "home_avg_starter_rating_pct":                    "Starter rating percentile (home)",
        f"home_roll_shooting_accuracy_last{n}":          "Shooting accuracy (home)",
        f"home_roll_corner_ratio_last{n}":               "Corner ratio (home)",
        f"home_roll_corners_against_last{n}":            "Corners against (home)",
        f"home_roll_yellow_cards_last{n}":               "Yellow cards (home)",
        "home_season_draw_rate":                         "Draw rate (home)",
        f"home_roll_std_goals_for_last{n}":              "Goals for std dev (home)",
        f"home_roll_std_goals_against_last{n}":          "Goals against std dev (home)",
    }
    for col, label in checks.items():
        if col in dataset.columns:
            null_pct = dataset[col].isna().mean()
            flag = "  ⚠" if null_pct > 0.4 else "  ✓"
            print(f"  {flag}  {label:<40} {null_pct:.1%} nulos")
        else:
            print(f"       {label:<40} columna no encontrada")

    print(f"{'='*55}\n")


# ---------------------------------------------------------------------------
# Entrypoint
# ---------------------------------------------------------------------------

def parse_args():
    p = argparse.ArgumentParser(description="Build ML training dataset from PostgreSQL")
    p.add_argument("--lookback", type=int, default=DEFAULT_LOOKBACK,
                   help=f"Rolling window size (default: {DEFAULT_LOOKBACK})")
    p.add_argument("--h2h",      type=int, default=DEFAULT_H2H,
                   help=f"H2H lookback matches (default: {DEFAULT_H2H})")
    p.add_argument("--output",   type=Path, default=OUTPUT_FILE,
                   help=f"Output file path (default: {OUTPUT_FILE})")
    p.add_argument("--no-player-stats", action="store_true",
                   help="Omitir features de jugadores (más rápido, menos features)")
    return p.parse_args()


def main():
    args = parse_args()

    print("Conectando a PostgreSQL...")
    try:
        conn = get_connection()
    except Exception as e:
        print(f"ERROR de conexión: {e}")
        print("Revisa DB_CONFIG al inicio del script.")
        sys.exit(1)

    print("Cargando fixtures y estadísticas de equipo...")
    fixtures   = load_fixtures(conn)
    team_stats = load_team_stats(conn)
    print(f"  → {len(fixtures)} fixtures FT  |  {len(team_stats)} registros team_stats")

    player_rolling = None
    player_h2h     = None
    lineup_pct     = None
    aligned_pct    = None  # para diagnostics de cobertura de percentiles

    if not args.no_player_stats:
        print("Cargando estadísticas de jugadores...")
        player_stats = load_player_stats(conn)
        print(f"  → {len(player_stats)} registros fixture_player_stats")

        if not player_stats.empty:
            print("Calculando percentiles temporales de jugadores (sin leakage)...")
            temporal_pct = compute_player_season_percentiles_temporal(player_stats, fixtures)
            print(f"  → {len(temporal_pct)} snapshots fixture×jugador")
            aligned_pct = temporal_pct
        else:
            aligned_pct = None

        if not player_stats.empty and aligned_pct is not None and not aligned_pct.empty:

            print("Construyendo historial de jugadores por equipo...")
            player_history = build_team_player_history(player_stats, fixtures)

            print(f"Calculando rolling player last-{args.lookback}...")
            player_rolling = compute_rolling_player_features(player_history, args.lookback)

            print(f"Calculando H2H de jugadores last-{args.h2h}...")
            player_h2h = compute_h2h_player_features(player_stats, fixtures, args.h2h)

            print("Calculando features de calidad de alineación (percentiles calculados)...")
            lineup_pct = compute_lineup_percentile_features(player_stats, aligned_pct)
        else:
            print("  ⚠ Sin percentiles temporales — se omitirán features de percentiles")
            lineup_pct = None
            player_rolling = None
            player_h2h = None
    else:
        print("(Skipping player stats por --no-player-stats)")

    conn.close()

    print(f"Construyendo historial por equipo...")
    history = build_team_history(fixtures, team_stats)

    print(f"Calculando rolling last-{args.lookback}...")
    rolling       = compute_rolling_features(history, args.lookback)
    rolling_venue = compute_rolling_home_away(history, args.lookback)
    rest          = compute_days_rest(history)

    print(f"Calculando H2H last-{args.h2h}...")
    h2h = compute_h2h_features(fixtures, args.h2h)

    print("Calculando forma de temporada (season PPG/GPG)...")
    season_form = compute_season_form(fixtures)

    print("Calculando draw tendency features (season draw rate)...")
    draw_features = compute_season_draw_features(fixtures)

    print(f"Calculando consistency features (rolling std, last-{args.lookback})...")
    consistency = compute_consistency_features(history, args.lookback)

    print(f"Calculando EMA (span={args.lookback})...")
    ema = compute_ema_features(history, args.lookback)

    print("Calculando league base rates...")
    league_rates = compute_league_rates(fixtures)

    print("Ensamblando dataset...")
    dataset = assemble_dataset(
        fixtures, rolling, rolling_venue, rest, h2h, season_form,
        args.lookback, player_rolling, player_h2h, ema, league_rates, lineup_pct,
        draw_features=draw_features,
        consistency=consistency,
    )

    print_diagnostics(fixtures, dataset, args.lookback, aligned_pct)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    dataset.to_parquet(args.output, index=False)
    print(f"Dataset guardado en: {args.output}")
    print(f"Siguiente paso → Fase 3: train_model.py")


if __name__ == "__main__":
    main()
