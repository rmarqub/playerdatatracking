"""
Feature engineering para el modelo predictivo de partidos — v3.

Cambios respecto a v2:
  - compute_league_season_draw_rate(): draw rate de la liga en la temporada actual
    (temporal, shift, sin leakage). Complementa league_draw_rate histórico.
  - Nuevas features de cruce home×away para empates:
      both_draw_prone, draw_rate_diff_recent, both_teams_recent_draw_rate
  - Nuevas features de cruce home×away para OUC/BTTS:
      combined_xg, defensive_porosity, total_season_goal_rate,
      goal_threat_product, both_teams_score_rate, clean_sheet_clash

Uso:
    python feature_engineering_v3.py
    python feature_engineering_v3.py --lookback 7 --h2h 8 --output dataset_v3.parquet
"""

import argparse
import sys
from pathlib import Path
from typing import Optional

import numpy as np
import pandas as pd
import psycopg2
from psycopg2.extras import RealDictCursor

DB_CONFIG = {
    "host": "localhost",
    "port": 5432,
    "dbname": "playerdata",
    "user": "postgres",
    "password": "admin",
}

DEFAULT_LOOKBACK = 5
DEFAULT_H2H      = 5
OUTPUT_FILE      = Path(__file__).parent / "training_data.parquet"


def get_connection():
    return psycopg2.connect(**DB_CONFIG, cursor_factory=RealDictCursor)


def load_fixtures(conn) -> pd.DataFrame:
    query = """
        SELECT
            f.id, f.league_id, f.league_name, f.season, f.match_date,
            f.home_team_id, f.home_team_name, f.away_team_id, f.away_team_name,
            f.goals_home, f.goals_away
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
            ts.fixture_id, ts.team_id,
            ts.shots_on_goal, ts.shots_total, ts.ball_possession, ts.passes_pct,
            ts.corner_kicks, ts.fouls, ts.yellow_cards, ts.red_cards,
            ts.expected_goals, ts.goalkeeper_saves, ts.shots_inside_box
        FROM fixture_team_stats ts
        JOIN fixture f ON f.id = ts.fixture_id
        WHERE f.status_short = 'FT'
    """
    with conn.cursor() as cur:
        cur.execute(query)
        rows = cur.fetchall()
    return pd.DataFrame([dict(r) for r in rows])


def load_player_stats(conn) -> pd.DataFrame:
    query = """
        SELECT
            ps.fixture_id, ps.team_id, ps.player_id, ps.position, ps.substitute,
            ps.minutes_played, ps.rating::float AS rating,
            ps.goals_scored, ps.assists, ps.shots_on, ps.passes_key,
            ps.tackles_total, ps.interceptions, ps.duels_total, ps.duels_won,
            ps.saves, ps.goals_conceded,
            f.match_date, f.home_team_id, f.away_team_id
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


def build_team_history(fixtures: pd.DataFrame, team_stats: pd.DataFrame) -> pd.DataFrame:
    ts_idx = team_stats.set_index(["fixture_id", "team_id"]).to_dict("index")

    records = []
    for f in fixtures.itertuples(index=False):
        for venue, team_id, rival_id, g_for, g_against in [
            ("H", f.home_team_id, f.away_team_id, f.goals_home, f.goals_away),
            ("A", f.away_team_id, f.home_team_id, f.goals_away, f.goals_home),
        ]:
            ts       = ts_idx.get((f.id, team_id), {})
            rival_ts = ts_idx.get((f.id, rival_id), {})
            records.append({
                "fixture_id":        f.id,
                "match_date":        f.match_date,
                "team_id":           team_id,
                "venue":             venue,
                "goals_for":         g_for,
                "goals_against":     g_against,
                "won":               1 if g_for > g_against else 0,
                "drew":              1 if g_for == g_against else 0,
                "lost":              1 if g_for < g_against else 0,
                "scored":            1 if g_for > 0 else 0,
                "clean_sheet":       1 if g_against == 0 else 0,
                "btts":              1 if (g_for > 0 and g_against > 0) else 0,
                "ou25":              1 if (g_for + g_against) > 2.5 else 0,
                "ou15":              1 if (g_for + g_against) > 1.5 else 0,
                "xg_for":            ts.get("expected_goals"),
                "xg_against":        rival_ts.get("expected_goals"),
                "shots_on_goal":     ts.get("shots_on_goal"),
                "shots_total":       ts.get("shots_total"),
                "possession":        ts.get("ball_possession"),
                "passes_pct":        ts.get("passes_pct"),
                "corner_kicks":      ts.get("corner_kicks"),
                "saves":             ts.get("goalkeeper_saves"),
                "shots_inside_box":  ts.get("shots_inside_box"),
                "fouls":             ts.get("fouls"),
                "yellow_cards":      ts.get("yellow_cards"),
                "corners_against":   rival_ts.get("corner_kicks"),
            })

    df = pd.DataFrame(records)
    shots_total_safe = df["shots_total"].replace(0, np.nan)
    df["shooting_accuracy"]     = df["shots_on_goal"] / shots_total_safe
    df["shots_inside_box_rate"] = df["shots_inside_box"] / shots_total_safe
    df["corner_ratio"]          = df["corner_kicks"] / (df["corner_kicks"] + df["corners_against"] + 1e-6)
    df["fouls_per_shot"]        = df["fouls"] / shots_total_safe
    df = df.sort_values(["team_id", "match_date"]).reset_index(drop=True)
    return df


PLAYER_ROLL_COLS = [
    "avg_rating", "goals_pstarted", "key_passes_pstarted", "def_actions_pstarted",
    "duel_win_pct", "gk_avg_rating", "gk_save_pct",
    "avg_rating_d", "avg_rating_m", "avg_rating_f",
    "max_scorer_goals", "goals_concentration", "lineup_continuity",
]


def _compute_lineup_continuity(starters: pd.DataFrame) -> pd.DataFrame:
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
    if player_stats.empty:
        return pd.DataFrame()

    starters = player_stats[player_stats["substitute"] == False].copy()
    outfield  = starters[starters["position"] != "G"].copy()
    outfield["def_actions"] = outfield["tackles_total"].fillna(0) + outfield["interceptions"].fillna(0)
    outfield_agg = outfield.groupby(["fixture_id", "team_id"]).agg(
        sum_def_actions=("def_actions", "sum"),
        n_outfield=("player_id", "count"),
    ).reset_index()

    starter_agg = starters.groupby(["fixture_id", "team_id"]).agg(
        n_starters=("player_id", "count"),
        avg_rating=("rating", "mean"),
        sum_goals=("goals_scored", "sum"),
        sum_key_passes=("passes_key", "sum"),
        sum_duels_won=("duels_won", "sum"),
        sum_duels_total=("duels_total", "sum"),
        max_scorer_goals=("goals_scored", "max"),
    ).reset_index()

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
    lineup_cont = _compute_lineup_continuity(starters)

    agg = starter_agg.merge(outfield_agg, on=["fixture_id", "team_id"], how="left")
    for df_part in [gk_agg, def_agg, mid_agg, att_agg, lineup_cont]:
        if not df_part.empty:
            agg = agg.merge(df_part, on=["fixture_id", "team_id"], how="left")

    agg["goals_pstarted"]       = agg["sum_goals"] / agg["n_starters"]
    agg["key_passes_pstarted"]  = agg["sum_key_passes"] / agg["n_starters"]
    agg["def_actions_pstarted"] = agg["sum_def_actions"] / agg["n_outfield"].replace(0, np.nan)
    agg["duel_win_pct"]         = agg["sum_duels_won"] / agg["sum_duels_total"].replace(0, np.nan)
    agg["goals_concentration"]  = agg["max_scorer_goals"] / agg["sum_goals"].replace(0, np.nan)

    mask = agg["n_starters"] < 6
    for col in PLAYER_ROLL_COLS:
        if col in agg.columns:
            agg.loc[mask, col] = np.nan

    fixture_dates = fixtures[["id", "match_date"]].rename(columns={"id": "fixture_id"})
    result = agg.merge(fixture_dates, on="fixture_id", how="left")
    result = result.sort_values(["team_id", "match_date"]).reset_index(drop=True)
    keep_cols = ["fixture_id", "team_id", "match_date"] + [c for c in PLAYER_ROLL_COLS if c in result.columns]
    return result[keep_cols]


ROLL_COLS = [
    "goals_for", "goals_against", "won", "drew", "lost",
    "scored", "clean_sheet",
    "xg_for", "xg_against", "shots_on_goal", "shots_total",
    "possession", "passes_pct", "corner_kicks", "saves",
    "shooting_accuracy", "shots_inside_box_rate",
    "corners_against", "corner_ratio",
    "fouls_per_shot", "yellow_cards", "fouls",
    "btts", "ou25", "ou15",  # tasa histórica de mercados por equipo
]

EMA_COLS = ["goals_for", "goals_against", "won", "scored", "clean_sheet", "xg_for", "xg_against", "shots_on_goal"]


def _rolling_mean(series: pd.Series, n: int) -> pd.Series:
    return series.shift(1).rolling(n, min_periods=1).mean()


def compute_rolling_features(history: pd.DataFrame, n: int) -> pd.DataFrame:
    rolled_parts = []
    for team_id, grp in history.groupby("team_id", sort=False):
        grp = grp.sort_values("match_date")
        part = grp[["fixture_id", "team_id", "venue", "match_date"]].copy()
        for col in ROLL_COLS:
            part[f"roll_{col}_last{n}"] = _rolling_mean(grp[col].reset_index(drop=True), n).values
        rolled_parts.append(part)
    return pd.concat(rolled_parts, ignore_index=True)


def compute_rolling_home_away(history: pd.DataFrame, n: int) -> pd.DataFrame:
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
    parts = []
    for team_id, grp in history.groupby("team_id", sort=False):
        grp = grp.sort_values("match_date").copy()
        grp["days_rest"] = grp["match_date"].diff().dt.days
        parts.append(grp[["fixture_id", "team_id", "days_rest"]])
    return pd.concat(parts, ignore_index=True)


def compute_season_form(fixtures: pd.DataFrame) -> pd.DataFrame:
    records = []
    for f in fixtures.itertuples(index=False):
        for team_id, g_for, g_against in [
            (f.home_team_id, f.goals_home, f.goals_away),
            (f.away_team_id, f.goals_away, f.goals_home),
        ]:
            pts = 3 if g_for > g_against else (1 if g_for == g_against else 0)
            records.append({
                "fixture_id": f.id, "team_id": team_id, "season": f.season,
                "match_date": f.match_date, "points": pts,
                "goals_for": g_for, "goals_against": g_against,
            })

    df = pd.DataFrame(records).sort_values(["team_id", "season", "match_date"])
    parts = []
    for (team_id, season), grp in df.groupby(["team_id", "season"], sort=False):
        grp = grp.sort_values("match_date").copy()
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


def compute_season_draw_features(fixtures: pd.DataFrame) -> pd.DataFrame:
    """Draw rate por equipo en la temporada actual (shift, sin leakage)."""
    records = []
    for f in fixtures.itertuples(index=False):
        for team_id, g_for, g_against in [
            (f.home_team_id, f.goals_home, f.goals_away),
            (f.away_team_id, f.goals_away, f.goals_home),
        ]:
            records.append({
                "fixture_id": f.id, "team_id": team_id,
                "season": f.season, "match_date": f.match_date,
                "drew": 1 if g_for == g_against else 0,
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


def compute_league_season_draw_rate(fixtures: pd.DataFrame) -> pd.DataFrame:
    """
    Draw rate de la liga en la temporada actual (shift temporal, sin leakage).
    Distinto de league_draw_rate (histórico cross-temporadas): captura si la liga
    concreta está siendo más defensiva/igualada en ese año específico.
    Requiere ≥ 5 partidos previos en esa liga-temporada; si no, NaN.
    """
    fs = fixtures.sort_values("match_date").reset_index(drop=True).copy()
    fs["draw_flag"] = (fs["goals_home"] == fs["goals_away"]).astype(float)

    parts = []
    for (league_id, season), grp in fs.groupby(["league_id", "season"], sort=False):
        grp = grp.sort_values("match_date").copy()
        shifted = grp["draw_flag"].shift(1)
        count   = shifted.expanding().count()
        grp["league_season_draw_rate"] = shifted.expanding().mean()
        grp.loc[count < 5, "league_season_draw_rate"] = np.nan
        parts.append(grp[["id", "league_season_draw_rate"]])

    return pd.concat(parts, ignore_index=True).rename(columns={"id": "fixture_id"})


def compute_consistency_features(history: pd.DataFrame, n: int) -> pd.DataFrame:
    parts = []
    for team_id, grp in history.groupby("team_id", sort=False):
        grp = grp.sort_values("match_date")
        part = grp[["fixture_id", "team_id", "venue", "match_date"]].copy()
        for col in ["goals_for", "goals_against"]:
            shifted = grp[col].reset_index(drop=True).shift(1)
            part[f"roll_std_{col}_last{n}"] = shifted.rolling(n, min_periods=2).std().values
        parts.append(part)
    return pd.concat(parts, ignore_index=True)


def compute_ema_features(history: pd.DataFrame, span: int) -> pd.DataFrame:
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


def compute_league_rates(fixtures: pd.DataFrame) -> pd.DataFrame:
    fs = fixtures.sort_values("match_date").reset_index(drop=True).copy()
    fs["home_win_flag"]  = (fs["goals_home"] > fs["goals_away"]).astype(float)
    fs["draw_flag"]      = (fs["goals_home"] == fs["goals_away"]).astype(float)
    fs["away_win_flag"]  = (fs["goals_home"] < fs["goals_away"]).astype(float)
    fs["total_goals_f"]  = fs["goals_home"] + fs["goals_away"]
    fs["over25_flag"]    = (fs["total_goals_f"] > 2.5).astype(float)
    fs["over15_flag"]    = (fs["total_goals_f"] > 1.5).astype(float)
    fs["btts_flag"]      = ((fs["goals_home"] > 0) & (fs["goals_away"] > 0)).astype(float)

    parts = []
    for league_id, grp in fs.groupby("league_id", sort=False):
        grp = grp.sort_values("match_date").copy()
        count = grp["home_win_flag"].shift(1).expanding().count()
        grp["league_home_win_rate"] = grp["home_win_flag"].shift(1).expanding().mean()
        grp["league_draw_rate"]     = grp["draw_flag"].shift(1).expanding().mean()
        grp["league_away_win_rate"] = grp["away_win_flag"].shift(1).expanding().mean()
        grp["league_avg_goals"]     = grp["total_goals_f"].shift(1).expanding().mean()
        grp["league_over25_rate"]   = grp["over25_flag"].shift(1).expanding().mean()
        grp["league_over15_rate"]   = grp["over15_flag"].shift(1).expanding().mean()
        grp["league_btts_rate"]     = grp["btts_flag"].shift(1).expanding().mean()
        league_cols = [
            "league_home_win_rate", "league_draw_rate", "league_away_win_rate",
            "league_avg_goals", "league_over25_rate", "league_over15_rate", "league_btts_rate",
        ]
        for col in league_cols:
            grp.loc[count < 10, col] = np.nan
        parts.append(grp[["id"] + league_cols])

    return pd.concat(parts, ignore_index=True).rename(columns={"id": "fixture_id"})


def compute_rolling_player_features(player_history: pd.DataFrame, n: int) -> pd.DataFrame:
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


def compute_h2h_features(fixtures: pd.DataFrame, m: int) -> pd.DataFrame:
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
            records.append({"fixture_id": f.id, "h2h_home_wins": np.nan,
                            "h2h_draws": np.nan, "h2h_away_wins": np.nan,
                            "h2h_avg_goals": np.nan, "h2h_count": 0})
            continue
        n_matches = len(past)
        home_wins = sum(
            (r.home_team_id == home and r.goals_home > r.goals_away) or
            (r.away_team_id == home and r.goals_away > r.goals_home)
            for r in past.itertuples(index=False)
        )
        draws     = sum(r.goals_home == r.goals_away for r in past.itertuples(index=False))
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


def compute_h2h_player_features(
    player_stats: pd.DataFrame,
    fixtures: pd.DataFrame,
    m: int,
) -> pd.DataFrame:
    empty_row = {
        "h2h_home_avg_rating":     np.nan, "h2h_away_avg_rating":     np.nan,
        "h2h_home_goals_pstarted": np.nan, "h2h_away_goals_pstarted": np.nan,
    }
    if player_stats.empty:
        return pd.DataFrame([{"fixture_id": fid, **empty_row} for fid in fixtures["id"]])

    fixtures_sorted = fixtures.sort_values("match_date").reset_index(drop=True)
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
            .tail(m)["id"].tolist()
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


def compute_player_season_percentiles_temporal(
    player_stats: pd.DataFrame,
    fixtures: pd.DataFrame,
    min_minutes: int = 90,
) -> pd.DataFrame:
    if player_stats.empty:
        return pd.DataFrame()

    fix_meta = fixtures[["id", "league_id", "season"]].rename(columns={"id": "fixture_id"})
    ps = player_stats.merge(fix_meta, on="fixture_id", how="left")
    ps = ps[ps["minutes_played"].fillna(0) > 0].copy()
    ps["match_date"]   = pd.to_datetime(ps["match_date"], utc=True)
    ps["rating_float"] = pd.to_numeric(ps["rating"], errors="coerce")

    results = []
    for (league_id, season), lg in ps.groupby(["league_id", "season"]):
        lg = lg.sort_values("match_date")
        date_groups = (
            lg[["fixture_id", "match_date"]]
            .drop_duplicates("fixture_id")
            .groupby("match_date")["fixture_id"].apply(list)
            .reset_index()
            .sort_values("match_date")
        )
        cum: dict = {}
        for _, drow in date_groups.iterrows():
            fdate = drow["match_date"]
            fids  = drow["fixture_id"]
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


def build_targets(fixtures: pd.DataFrame, team_stats: pd.DataFrame | None = None) -> pd.DataFrame:
    df = fixtures.copy()
    df["result"] = np.where(
        df["goals_home"] > df["goals_away"], 0,
        np.where(df["goals_home"] == df["goals_away"], 1, 2)
    )
    total = df["goals_home"] + df["goals_away"]
    df["over05"] = (total > 0.5).astype(int)
    df["over15"] = (total > 1.5).astype(int)
    df["over25"] = (total > 2.5).astype(int)
    df["over35"] = (total > 3.5).astype(int)
    df["btts"]   = ((df["goals_home"] > 0) & (df["goals_away"] > 0)).astype(int)
    df["total_goals"] = total
    # total_corners: suma de córners de ambos equipos por partido (requiere team_stats)
    if team_stats is not None and not team_stats.empty:
        tc = (
            team_stats.groupby("fixture_id")["corner_kicks"]
            .sum()
            .reset_index()
            .rename(columns={"corner_kicks": "total_corners"})
        )
        df = df.merge(tc, left_on="id", right_on="fixture_id", how="left").drop(columns=["fixture_id"], errors="ignore")
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
    league_season_draw_rate: Optional[pd.DataFrame] = None,
    team_stats: Optional[pd.DataFrame] = None,
) -> pd.DataFrame:
    base = build_targets(fixtures, team_stats=team_stats)

    def _fid(df: pd.DataFrame) -> pd.DataFrame:
        return df.rename(columns={"fixture_id": "id"}) if "fixture_id" in df.columns else df

    home_roll = rolling[rolling["venue"] == "H"].drop(columns=["venue", "match_date"])
    away_roll = rolling[rolling["venue"] == "A"].drop(columns=["venue", "match_date"])
    home_roll = _fid(home_roll).rename(columns={"team_id": "home_team_id"})
    home_roll = _suffix_roll_cols(home_roll, "home", exclude={"id", "home_team_id"})
    away_roll = _fid(away_roll).rename(columns={"team_id": "away_team_id"})
    away_roll = _suffix_roll_cols(away_roll, "away", exclude={"id", "away_team_id"})

    h_cols = [c for c in rolling_venue.columns if f"_h_last{n}" in c]
    a_cols = [c for c in rolling_venue.columns if f"_a_last{n}" in c]
    home_venue = _fid(
        rolling_venue[rolling_venue["venue"] == "H"][["fixture_id", "team_id"] + h_cols].copy()
    ).rename(columns={"team_id": "home_team_id"})
    away_venue = _fid(
        rolling_venue[rolling_venue["venue"] == "A"][["fixture_id", "team_id"] + a_cols].copy()
    ).rename(columns={"team_id": "away_team_id"})

    rest_norm = _fid(rest)
    home_rest = rest_norm.copy().rename(columns={"team_id": "home_team_id", "days_rest": "home_days_rest"})
    away_rest = rest_norm.copy().rename(columns={"team_id": "away_team_id", "days_rest": "away_days_rest"})

    sf = _fid(season_form)
    home_sf = sf.copy().rename(columns={
        "team_id": "home_team_id", "season_ppg": "home_season_ppg",
        "season_gfpg": "home_season_gfpg", "season_gapg": "home_season_gapg",
        "season_games": "home_season_games",
    })
    away_sf = sf.copy().rename(columns={
        "team_id": "away_team_id", "season_ppg": "away_season_ppg",
        "season_gfpg": "away_season_gfpg", "season_gapg": "away_season_gapg",
        "season_games": "away_season_games",
    })

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

    if draw_features is not None and not draw_features.empty:
        df_draw = _fid(draw_features)
        home_draw = df_draw.copy().rename(columns={
            "team_id": "home_team_id", "season_draw_rate": "home_season_draw_rate",
        })
        away_draw = df_draw.copy().rename(columns={
            "team_id": "away_team_id", "season_draw_rate": "away_season_draw_rate",
        })
        df = df.merge(home_draw, on=["id", "home_team_id"], how="left")
        df = df.merge(away_draw, on=["id", "away_team_id"], how="left")

    if consistency is not None and not consistency.empty:
        home_cons = consistency[consistency["venue"] == "H"].drop(columns=["venue", "match_date"])
        away_cons = consistency[consistency["venue"] == "A"].drop(columns=["venue", "match_date"])
        home_cons = _fid(home_cons).rename(columns={"team_id": "home_team_id"})
        home_cons = _suffix_roll_cols(home_cons, "home", exclude={"id", "home_team_id"})
        away_cons = _fid(away_cons).rename(columns={"team_id": "away_team_id"})
        away_cons = _suffix_roll_cols(away_cons, "away", exclude={"id", "away_team_id"})
        df = df.merge(home_cons, on=["id", "home_team_id"], how="left")
        df = df.merge(away_cons, on=["id", "away_team_id"], how="left")

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

    if player_h2h is not None and not player_h2h.empty:
        df = df.merge(_fid(player_h2h), on="id", how="left")

    if ema is not None and not ema.empty:
        home_ema = ema[ema["venue"] == "H"].drop(columns=["venue", "match_date"])
        away_ema = ema[ema["venue"] == "A"].drop(columns=["venue", "match_date"])
        home_ema = _fid(home_ema).rename(columns={"team_id": "home_team_id"})
        home_ema = _suffix_roll_cols(home_ema, "home", exclude={"id", "home_team_id"})
        away_ema = _fid(away_ema).rename(columns={"team_id": "away_team_id"})
        away_ema = _suffix_roll_cols(away_ema, "away", exclude={"id", "away_team_id"})
        df = df.merge(home_ema, on=["id", "home_team_id"], how="left")
        df = df.merge(away_ema, on=["id", "away_team_id"], how="left")

    if league_rates is not None and not league_rates.empty:
        df = df.merge(_fid(league_rates), on="id", how="left")

    if league_season_draw_rate is not None and not league_season_draw_rate.empty:
        df = df.merge(_fid(league_season_draw_rate), on="id", how="left")

    if lineup_pct is not None and not lineup_pct.empty:
        home_pct = _fid(lineup_pct).rename(columns={"team_id": "home_team_id"})
        home_pct = _suffix_roll_cols(home_pct, "home", exclude={"id", "home_team_id"})
        away_pct = _fid(lineup_pct).rename(columns={"team_id": "away_team_id"})
        away_pct = _suffix_roll_cols(away_pct, "away", exclude={"id", "away_team_id"})
        df = df.merge(home_pct, on=["id", "home_team_id"], how="left")
        df = df.merge(away_pct, on=["id", "away_team_id"], how="left")

    df = df.rename(columns={"id": "fixture_id"})

    # ---- Features de balance para empates ----
    xg_h = f"home_roll_xg_for_last{n}"
    xg_a = f"away_roll_xg_for_last{n}"
    if xg_h in df.columns and xg_a in df.columns:
        xg_sum = df[xg_h] + df[xg_a] + 1e-6
        df["xg_balance"] = 1.0 - (df[xg_h] - df[xg_a]).abs() / xg_sum

    if "home_season_ppg" in df.columns and "away_season_ppg" in df.columns:
        ppg_max = df[["home_season_ppg", "away_season_ppg"]].max(axis=1)
        ppg_min = df[["home_season_ppg", "away_season_ppg"]].min(axis=1)
        df["ppg_balance"] = ppg_min / (ppg_max + 1e-6)

    if "home_season_draw_rate" in df.columns and "away_season_draw_rate" in df.columns:
        min_dr = df[["home_season_draw_rate", "away_season_draw_rate"]].min(axis=1)
        max_dr = df[["home_season_draw_rate", "away_season_draw_rate"]].max(axis=1)
        df["draw_tendency_index"] = min_dr / (max_dr + 1e-6)

    if "ppg_balance" in df.columns and "xg_balance" in df.columns:
        df["match_balance_index"] = df[["ppg_balance", "xg_balance"]].mean(axis=1)

    if "league_draw_rate" in df.columns and "home_season_draw_rate" in df.columns and "away_season_draw_rate" in df.columns:
        team_draw_mean = df[["home_season_draw_rate", "away_season_draw_rate"]].mean(axis=1)
        df["team_draw_vs_league"] = team_draw_mean - df["league_draw_rate"]

    if "league_avg_goals" in df.columns and "home_season_gfpg" in df.columns and "away_season_gfpg" in df.columns:
        team_goal_expectation = df["home_season_gfpg"] + df["away_season_gfpg"]
        df["team_goals_vs_league"] = team_goal_expectation - df["league_avg_goals"]

    # ---- v3: nuevas features de cruce home×away ----

    # Empates: ambos equipos propensos a empatar simultáneamente
    if "home_season_draw_rate" in df.columns and "away_season_draw_rate" in df.columns:
        df["both_draw_prone"] = df[["home_season_draw_rate", "away_season_draw_rate"]].min(axis=1)

    # Empates: diferencia y media en draw rate reciente (rolling)
    h_drew = f"home_roll_drew_last{n}"
    a_drew = f"away_roll_drew_last{n}"
    if h_drew in df.columns and a_drew in df.columns:
        df["draw_rate_diff_recent"]      = df[h_drew] - df[a_drew]
        df["both_teams_recent_draw_rate"] = (df[h_drew] + df[a_drew]) / 2

    # OUC: expectativa conjunta de goles del partido
    xga_h = f"home_roll_xg_against_last{n}"
    xga_a = f"away_roll_xg_against_last{n}"
    if xg_h in df.columns and xg_a in df.columns:
        df["combined_xg"] = df[xg_h] + df[xg_a]
    if xga_h in df.columns and xga_a in df.columns:
        df["defensive_porosity"] = df[xga_h] + df[xga_a]

    ck_h = f"home_roll_corner_kicks_last{n}"
    ck_a = f"away_roll_corner_kicks_last{n}"
    if ck_h in df.columns and ck_a in df.columns:
        df["combined_corners"] = df[ck_h] + df[ck_a]

    if "home_season_gfpg" in df.columns and "away_season_gfpg" in df.columns:
        df["total_season_goal_rate"] = df["home_season_gfpg"] + df["away_season_gfpg"]
        # Multiplicativo: alta cuando ambos equipos atacan, baja si uno es defensivo
        df["goal_threat_product"]    = df["home_season_gfpg"] * df["away_season_gfpg"]

    # BTTS: probabilidad conjunta de que ambos equipos marquen
    sc_h = f"home_roll_scored_last{n}"
    sc_a = f"away_roll_scored_last{n}"
    cs_h = f"home_roll_clean_sheet_last{n}"
    cs_a = f"away_roll_clean_sheet_last{n}"
    if sc_h in df.columns and sc_a in df.columns:
        df["both_teams_score_rate"] = df[sc_h] * df[sc_a]
    if cs_h in df.columns and cs_a in df.columns:
        # Alta cuando ambas defensas son sólidas → señal negativa para BTTS
        df["clean_sheet_clash"] = df[cs_h] * df[cs_a]

    # ---- Features diferenciales home-minus-away ----
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
        (f"home_roll_corner_kicks_last{n}",          f"away_roll_corner_kicks_last{n}",          "diff_corners"),
        (f"home_roll_corners_against_last{n}",       f"away_roll_corners_against_last{n}",       "diff_corners_against"),
        (f"home_roll_corner_ratio_last{n}",          f"away_roll_corner_ratio_last{n}",          "corner_dominance_diff"),
        (f"home_roll_yellow_cards_last{n}",          f"away_roll_yellow_cards_last{n}",          "diff_yellow_cards"),
        (f"home_roll_fouls_per_shot_last{n}",        f"away_roll_fouls_per_shot_last{n}",        "diff_fouls_per_shot"),
        (f"home_roll_shooting_accuracy_last{n}",     f"away_roll_shooting_accuracy_last{n}",     "diff_shooting_accuracy"),
        (f"home_roll_shots_inside_box_rate_last{n}", f"away_roll_shots_inside_box_rate_last{n}", "diff_shots_inside_box_rate"),
    ]
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
        (f"home_ema_goals_for_span{n}",      f"away_ema_goals_for_span{n}",      "diff_ema_goals_for"),
        (f"home_ema_goals_against_span{n}",  f"away_ema_goals_against_span{n}",  "diff_ema_goals_against"),
        (f"home_ema_won_span{n}",            f"away_ema_won_span{n}",            "diff_ema_won"),
        (f"home_ema_scored_span{n}",         f"away_ema_scored_span{n}",         "diff_ema_scored"),
        (f"home_ema_clean_sheet_span{n}",    f"away_ema_clean_sheet_span{n}",    "diff_ema_clean_sheet"),
        (f"home_ema_xg_for_span{n}",         f"away_ema_xg_for_span{n}",         "diff_ema_xg"),
    ]
    pct_diff_pairs = [
        ("home_avg_att_goal_pct",       "away_avg_att_goal_pct",       "diff_att_goal_pct"),
        ("home_top_attacker_goal_pct",  "away_top_attacker_goal_pct",  "diff_top_attacker_pct"),
        ("home_avg_def_pct",            "away_avg_def_pct",            "diff_def_pct"),
        ("home_avg_starter_rating_pct", "away_avg_starter_rating_pct", "diff_starter_rating_pct"),
    ]
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
    print(f"    over05  — {dataset['over05'].mean():.1%} positivos" if "over05" in dataset.columns else "    over05  — columna no encontrada")
    print(f"    over15  — {dataset['over15'].mean():.1%} positivos" if "over15" in dataset.columns else "    over15  — columna no encontrada")
    print(f"    over25  — {dataset['over25'].mean():.1%} positivos")
    print(f"    over35  — {dataset['over35'].mean():.1%} positivos" if "over35" in dataset.columns else "    over35  — columna no encontrada")
    print(f"    btts    — {dataset['btts'].mean():.1%} positivos")
    if "total_corners" in dataset.columns:
        tc = dataset["total_corners"].dropna()
        print(f"    total_corners — media {tc.mean():.1f}  |  nulos {dataset['total_corners'].isna().mean():.1%}")

    if aligned_pct is not None and not aligned_pct.empty:
        pct_fixture_count = aligned_pct["fixture_id"].nunique()
        pct_coverage = pct_fixture_count / len(fixtures) * 100
        print(f"\n  Cobertura de percentiles de jugadores:")
        print(f"    Fixtures con percentiles:  {pct_fixture_count:>7} / {len(fixtures)} ({pct_coverage:.1f}%)")
        if pct_coverage < 80:
            print(f"    ⚠ Cobertura baja")

    print(f"\n  Completitud de features clave (nulos):")
    checks = {
        f"home_roll_xg_for_last{n}":               "xG for (home)",
        f"home_roll_goals_for_last{n}":             "Goals for (home)",
        "h2h_avg_goals":                            "H2H avg goals",
        "home_days_rest":                           "Days rest (home)",
        "league_home_win_rate":                     "League home win rate",
        "league_draw_rate":                         "League draw rate",
        "league_avg_goals":                         "League avg goals",
        "league_btts_rate":                         "League BTTS rate",
        "home_season_draw_rate":                    "Draw rate (home)",
        "away_season_draw_rate":                    "Draw rate (away)",
        "league_season_draw_rate":                  "League season draw rate (v3)",
        "both_draw_prone":                          "Both draw prone (v3)",
        "draw_rate_diff_recent":                    "Draw rate diff recent (v3)",
        "both_teams_recent_draw_rate":              "Both teams recent draw rate (v3)",
        "combined_xg":                              "Combined xG (v3)",
        "defensive_porosity":                       "Defensive porosity (v3)",
        "goal_threat_product":                      "Goal threat product (v3)",
        "both_teams_score_rate":                    "Both teams score rate (v3)",
        "clean_sheet_clash":                        "Clean sheet clash (v3)",
        f"home_roll_std_goals_for_last{n}":         "Goals for std dev (home)",
    }
    for col, label in checks.items():
        if col in dataset.columns:
            null_pct = dataset[col].isna().mean()
            flag = "  ⚠" if null_pct > 0.4 else "  ✓"
            print(f"  {flag}  {label:<45} {null_pct:.1%} nulos")
        else:
            print(f"       {label:<45} columna no encontrada")

    print(f"{'='*55}\n")


def parse_args():
    p = argparse.ArgumentParser(description="Build ML training dataset v3 from PostgreSQL")
    p.add_argument("--lookback", type=int, default=DEFAULT_LOOKBACK)
    p.add_argument("--h2h",      type=int, default=DEFAULT_H2H)
    p.add_argument("--output",   type=Path, default=OUTPUT_FILE)
    p.add_argument("--no-player-stats", action="store_true")
    return p.parse_args()


def main():
    args = parse_args()

    print("Conectando a PostgreSQL...")
    try:
        conn = get_connection()
    except Exception as e:
        print(f"ERROR de conexión: {e}")
        sys.exit(1)

    print("Cargando fixtures y estadísticas de equipo...")
    fixtures   = load_fixtures(conn)
    team_stats = load_team_stats(conn)
    print(f"  → {len(fixtures)} fixtures FT  |  {len(team_stats)} registros team_stats")

    player_rolling = None
    player_h2h     = None
    lineup_pct     = None
    aligned_pct    = None

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
            lineup_pct = player_rolling = player_h2h = None
    else:
        print("(Skipping player stats por --no-player-stats)")

    conn.close()

    print("Construyendo historial por equipo...")
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

    print("Calculando league season draw rate (v3)...")
    league_season_draw_rate = compute_league_season_draw_rate(fixtures)

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
        league_season_draw_rate=league_season_draw_rate,
        team_stats=team_stats,
    )

    print_diagnostics(fixtures, dataset, args.lookback, aligned_pct)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    dataset.to_parquet(args.output, index=False)
    print(f"Dataset guardado en: {args.output}")
    print(f"Siguiente paso → train_model_v3.py")


if __name__ == "__main__":
    main()
