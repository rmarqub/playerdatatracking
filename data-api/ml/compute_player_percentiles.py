"""
Calcula percentiles de jugadores a partir de player_match_stats y los persiste
en la tabla player_percentiles.

Dos scopes por jugador por temporada:
  - global  (league_id = 0): ranking entre todos los jugadores de la misma temporada
  - league  (league_id = N): ranking entre jugadores de la misma liga+temporada

Métricas:
  *_p90       → stat * 90 / minutos_totales  (stats de conteo)
  pass_accuracy / duels_won / dribbles_suc   → ratio won/total (porcentaje)
  avg_rating  → media de valoraciones

Uso:
    python compute_player_percentiles.py
    python compute_player_percentiles.py --season 2024
"""

import argparse
import datetime
import sys

import numpy as np
import pandas as pd
import psycopg2
from psycopg2.extras import RealDictCursor, execute_values

DB_CONFIG = {
    "host":     "localhost",
    "port":     5432,
    "dbname":   "playerdata",
    "user":     "postgres",
    "password": "admin",
}

MIN_MINUTES = 90
BATCH_SIZE  = 5_000

# ---------------------------------------------------------------------------
# DDL
# ---------------------------------------------------------------------------

CREATE_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS player_percentiles (
    id                    BIGSERIAL   PRIMARY KEY,
    player_id             BIGINT      NOT NULL,
    index_id              BIGINT,
    league_id             INTEGER     NOT NULL DEFAULT 0,
    season                TEXT        NOT NULL,
    pct_minutes           SMALLINT,
    pct_rating            SMALLINT,
    pct_goals_p90         SMALLINT,
    pct_assists_p90       SMALLINT,
    pct_shots_total_p90   SMALLINT,
    pct_shots_on_p90      SMALLINT,
    pct_passes_total_p90  SMALLINT,
    pct_passes_key_p90    SMALLINT,
    pct_pass_accuracy     SMALLINT,
    pct_tackles_p90       SMALLINT,
    pct_interceptions_p90 SMALLINT,
    pct_duels_won         SMALLINT,
    pct_dribbles_success  SMALLINT,
    pct_fouls_drawn_p90   SMALLINT,
    computed_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
)
"""

# Índice único separado: CREATE UNIQUE INDEX IF NOT EXISTS es idempotente,
# garantiza que el índice existe aunque la tabla se haya creado sin él.
_DDL_UNIQUE_IDX = """
CREATE UNIQUE INDEX IF NOT EXISTS uq_player_percentiles
    ON player_percentiles (player_id, league_id, season)
"""
_DDL_IDX_INDEX_ID = """
CREATE INDEX IF NOT EXISTS idx_pp_index_id
    ON player_percentiles (index_id, season)
"""
_DDL_IDX_PLAYER_SEASON = """
CREATE INDEX IF NOT EXISTS idx_pp_player_season
    ON player_percentiles (player_id, season)
"""

LOAD_STATS_SQL = """
SELECT
    ms.player_id,
    p.index_id,
    ms.league_id,
    ms.season,
    COALESCE(SUM(ms.minutes), 0)          AS total_minutes,
    AVG(ms.rating::float)                 AS avg_rating,
    COALESCE(SUM(ms.goals), 0)            AS total_goals,
    COALESCE(SUM(ms.assists), 0)          AS total_assists,
    COALESCE(SUM(ms.shots_total), 0)      AS total_shots_total,
    COALESCE(SUM(ms.shots_on), 0)         AS total_shots_on,
    COALESCE(SUM(ms.passes_total), 0)     AS total_passes_total,
    COALESCE(SUM(ms.passes_key), 0)       AS total_passes_key,
    COALESCE(SUM(ms.passes_acc), 0)       AS total_passes_acc,
    COALESCE(SUM(ms.tackles_total), 0)    AS total_tackles,
    COALESCE(SUM(ms.interceptions), 0)    AS total_interceptions,
    COALESCE(SUM(ms.duels_total), 0)      AS total_duels_total,
    COALESCE(SUM(ms.duels_won), 0)        AS total_duels_won,
    COALESCE(SUM(ms.dribbles_att), 0)     AS total_dribbles_att,
    COALESCE(SUM(ms.dribbles_suc), 0)     AS total_dribbles_suc,
    COALESCE(SUM(ms.fouls_drawn), 0)      AS total_fouls_drawn
FROM player_match_stats ms
LEFT JOIN player p ON p.id = ms.player_id
{season_filter}
GROUP BY ms.player_id, p.index_id, ms.league_id, ms.season
"""

UPSERT_SQL = """
INSERT INTO player_percentiles
    (player_id, index_id, league_id, season,
     pct_minutes, pct_rating,
     pct_goals_p90, pct_assists_p90,
     pct_shots_total_p90, pct_shots_on_p90,
     pct_passes_total_p90, pct_passes_key_p90, pct_pass_accuracy,
     pct_tackles_p90, pct_interceptions_p90,
     pct_duels_won, pct_dribbles_success, pct_fouls_drawn_p90,
     computed_at)
VALUES %s
ON CONFLICT (player_id, league_id, season) DO UPDATE SET
    index_id              = EXCLUDED.index_id,
    pct_minutes           = EXCLUDED.pct_minutes,
    pct_rating            = EXCLUDED.pct_rating,
    pct_goals_p90         = EXCLUDED.pct_goals_p90,
    pct_assists_p90       = EXCLUDED.pct_assists_p90,
    pct_shots_total_p90   = EXCLUDED.pct_shots_total_p90,
    pct_shots_on_p90      = EXCLUDED.pct_shots_on_p90,
    pct_passes_total_p90  = EXCLUDED.pct_passes_total_p90,
    pct_passes_key_p90    = EXCLUDED.pct_passes_key_p90,
    pct_pass_accuracy     = EXCLUDED.pct_pass_accuracy,
    pct_tackles_p90       = EXCLUDED.pct_tackles_p90,
    pct_interceptions_p90 = EXCLUDED.pct_interceptions_p90,
    pct_duels_won         = EXCLUDED.pct_duels_won,
    pct_dribbles_success  = EXCLUDED.pct_dribbles_success,
    pct_fouls_drawn_p90   = EXCLUDED.pct_fouls_drawn_p90,
    computed_at           = EXCLUDED.computed_at
"""

# (raw_column, pct_column) pairs — order must match UPSERT_SQL
METRIC_COLS = [
    ("total_minutes",     "pct_minutes"),
    ("avg_rating",        "pct_rating"),
    ("goals_p90",         "pct_goals_p90"),
    ("assists_p90",       "pct_assists_p90"),
    ("shots_total_p90",   "pct_shots_total_p90"),
    ("shots_on_p90",      "pct_shots_on_p90"),
    ("passes_total_p90",  "pct_passes_total_p90"),
    ("passes_key_p90",    "pct_passes_key_p90"),
    ("pass_accuracy",     "pct_pass_accuracy"),
    ("tackles_p90",       "pct_tackles_p90"),
    ("interceptions_p90", "pct_interceptions_p90"),
    ("duels_won_pct",     "pct_duels_won"),
    ("dribbles_suc_pct",  "pct_dribbles_success"),
    ("fouls_drawn_p90",   "pct_fouls_drawn_p90"),
]

PCT_COLS   = [pct for _, pct in METRIC_COLS]
NEEDED_COLS = ["player_id", "index_id", "league_id", "season"] + PCT_COLS


# ---------------------------------------------------------------------------
# DB helpers
# ---------------------------------------------------------------------------

def get_connection():
    return psycopg2.connect(**DB_CONFIG, cursor_factory=RealDictCursor)


def ensure_table(conn) -> None:
    with conn.cursor() as cur:
        cur.execute(CREATE_TABLE_SQL)
        cur.execute(_DDL_UNIQUE_IDX)
        cur.execute(_DDL_IDX_INDEX_ID)
        cur.execute(_DDL_IDX_PLAYER_SEASON)
    conn.commit()


def load_stats(conn, season: str | None = None) -> pd.DataFrame:
    season_filter = f"WHERE ms.season = '{season}'" if season else ""
    sql = LOAD_STATS_SQL.format(season_filter=season_filter)
    with conn.cursor() as cur:
        cur.execute(sql)
        rows = cur.fetchall()
    if not rows:
        return pd.DataFrame()
    df = pd.DataFrame([dict(r) for r in rows])
    df["avg_rating"] = pd.to_numeric(df["avg_rating"], errors="coerce")
    return df


# ---------------------------------------------------------------------------
# Metric computation
# ---------------------------------------------------------------------------

def compute_metrics(df: pd.DataFrame) -> pd.DataFrame:
    """Add p90 and ratio columns to a totals DataFrame."""
    df = df.copy()
    mins = df["total_minutes"].replace(0, np.nan)

    df["goals_p90"]         = df["total_goals"]        * 90 / mins
    df["assists_p90"]       = df["total_assists"]       * 90 / mins
    df["shots_total_p90"]   = df["total_shots_total"]   * 90 / mins
    df["shots_on_p90"]      = df["total_shots_on"]      * 90 / mins
    df["passes_total_p90"]  = df["total_passes_total"]  * 90 / mins
    df["passes_key_p90"]    = df["total_passes_key"]    * 90 / mins
    df["tackles_p90"]       = df["total_tackles"]       * 90 / mins
    df["interceptions_p90"] = df["total_interceptions"] * 90 / mins
    df["fouls_drawn_p90"]   = df["total_fouls_drawn"]   * 90 / mins

    df["pass_accuracy"] = np.where(
        df["total_passes_total"] > 0,
        df["total_passes_acc"] / df["total_passes_total"] * 100,
        np.nan,
    )
    df["duels_won_pct"] = np.where(
        df["total_duels_total"] > 0,
        df["total_duels_won"] / df["total_duels_total"] * 100,
        np.nan,
    )
    df["dribbles_suc_pct"] = np.where(
        df["total_dribbles_att"] > 0,
        df["total_dribbles_suc"] / df["total_dribbles_att"] * 100,
        np.nan,
    )
    return df


def add_percentile_ranks(df: pd.DataFrame, group_cols: list) -> pd.DataFrame:
    """
    Within each group defined by group_cols, rank each player and convert to
    percentile (0–100). Appends pct_* columns to df.
    """
    df = df.copy()
    for raw_col, pct_col in METRIC_COLS:
        if raw_col not in df.columns:
            df[pct_col] = None
            continue
        df[pct_col] = (
            df.groupby(group_cols)[raw_col]
            .transform(lambda x: x.rank(pct=True, na_option="keep") * 100)
            .round()
        )
    return df


def aggregate_global(df: pd.DataFrame) -> pd.DataFrame:
    """Collapse per-league rows into one row per (player, season)."""
    return (
        df.groupby(["player_id", "index_id", "season"], as_index=False)
        .agg(
            total_minutes      = ("total_minutes",      "sum"),
            avg_rating         = ("avg_rating",         "mean"),
            total_goals        = ("total_goals",        "sum"),
            total_assists      = ("total_assists",       "sum"),
            total_shots_total  = ("total_shots_total",  "sum"),
            total_shots_on     = ("total_shots_on",     "sum"),
            total_passes_total = ("total_passes_total", "sum"),
            total_passes_key   = ("total_passes_key",   "sum"),
            total_passes_acc   = ("total_passes_acc",   "sum"),
            total_tackles      = ("total_tackles",      "sum"),
            total_interceptions = ("total_interceptions","sum"),
            total_duels_total  = ("total_duels_total",  "sum"),
            total_duels_won    = ("total_duels_won",    "sum"),
            total_dribbles_att = ("total_dribbles_att", "sum"),
            total_dribbles_suc = ("total_dribbles_suc", "sum"),
            total_fouls_drawn  = ("total_fouls_drawn",  "sum"),
        )
    )


# ---------------------------------------------------------------------------
# Row builder & persistence
# ---------------------------------------------------------------------------

def _to_int(v) -> int | None:
    if v is None:
        return None
    try:
        f = float(v)
        return None if np.isnan(f) else int(f)
    except (TypeError, ValueError):
        return None


def build_rows_df(df: pd.DataFrame) -> list[tuple]:
    now = datetime.datetime.utcnow()
    rows = []
    for _, r in df.iterrows():
        rows.append((
            int(r["player_id"]),
            _to_int(r.get("index_id")),
            int(r["league_id"]),
            str(r["season"]),
            _to_int(r.get("pct_minutes")),
            _to_int(r.get("pct_rating")),
            _to_int(r.get("pct_goals_p90")),
            _to_int(r.get("pct_assists_p90")),
            _to_int(r.get("pct_shots_total_p90")),
            _to_int(r.get("pct_shots_on_p90")),
            _to_int(r.get("pct_passes_total_p90")),
            _to_int(r.get("pct_passes_key_p90")),
            _to_int(r.get("pct_pass_accuracy")),
            _to_int(r.get("pct_tackles_p90")),
            _to_int(r.get("pct_interceptions_p90")),
            _to_int(r.get("pct_duels_won")),
            _to_int(r.get("pct_dribbles_success")),
            _to_int(r.get("pct_fouls_drawn_p90")),
            now,
        ))
    return rows


def upsert_rows(conn, rows: list[tuple]) -> int:
    if not rows:
        return 0
    inserted = 0
    for i in range(0, len(rows), BATCH_SIZE):
        batch = rows[i : i + BATCH_SIZE]
        with conn.cursor() as cur:
            execute_values(cur, UPSERT_SQL, batch, page_size=BATCH_SIZE)
        conn.commit()
        inserted += len(batch)
    return inserted


# ---------------------------------------------------------------------------
# Main logic (callable from CLI or predict_api.py)
# ---------------------------------------------------------------------------

def run(season: str | None = None) -> int:
    """
    Execute the full percentile computation pipeline.
    Returns the number of rows upserted.
    """
    print("Conectando a PostgreSQL...")
    conn = get_connection()

    ensure_table(conn)

    lbl = f" (season={season})" if season else ""
    print(f"Cargando estadísticas de player_match_stats{lbl}...")
    df = load_stats(conn, season=season)

    if df.empty:
        print("Sin datos en player_match_stats.")
        conn.close()
        return 0

    df = df[df["total_minutes"] >= MIN_MINUTES].copy()
    print(f"  → {len(df)} registros (jugadores × liga × temporada) con ≥{MIN_MINUTES} min")

    # --- Global percentiles ---
    global_raw     = aggregate_global(df)
    global_metrics = compute_metrics(global_raw)
    global_ranked  = add_percentile_ranks(global_metrics, ["season"])
    global_ranked  = global_ranked.copy()
    global_ranked["league_id"] = 0

    # --- League percentiles ---
    league_metrics = compute_metrics(df)
    league_ranked  = add_percentile_ranks(league_metrics, ["league_id", "season"])

    combined = pd.concat(
        [global_ranked[NEEDED_COLS], league_ranked[NEEDED_COLS]],
        ignore_index=True,
    )

    print(f"  → Global: {len(global_ranked)} filas  |  Liga: {len(league_ranked)} filas")

    rows = build_rows_df(combined)
    print(f"Insertando {len(rows)} filas en player_percentiles...")
    n = upsert_rows(conn, rows)
    print(f"  → {n} filas insertadas/actualizadas")

    conn.close()
    return n


# ---------------------------------------------------------------------------
# CLI entry point
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="Calcula y persiste percentiles de jugadores desde player_match_stats"
    )
    parser.add_argument("--season", default=None,
                        help="Filtrar por temporada (ej: 2024)")
    args = parser.parse_args()

    try:
        run(season=args.season)
        print("Completado.")
    except Exception as e:
        print(f"ERROR: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
