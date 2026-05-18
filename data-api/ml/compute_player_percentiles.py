"""
Calcula percentiles de jugadores y los persiste en player_percentiles.

Modelo esperado:
- player.id: id interno de tu tabla player.
- player.index_id: id externo/API del jugador.
- fixture_player_stats.player_id: id externo/API del jugador, por eso se une con player.index_id.
- fixture_player_stats.fixture_id -> fixture.id, desde fixture se obtienen league_id y season.

Rows generadas:
- Una row por jugador interno + liga + temporada.
- Una row global por jugador interno + temporada con league_id = 0.

Actualización:
- Por defecto hace UPSERT sobre UNIQUE(player_id, league_id, season).
- Opcionalmente puede hacer TRUNCATE antes de recalcular todo.

Uso:
    python compute_player_percentiles_v2.py
    python compute_player_percentiles_v2.py --season 2024
    python compute_player_percentiles_v2.py --truncate
    python compute_player_percentiles_v2.py --min-minutes 90
"""

from __future__ import annotations

import argparse
import datetime as dt
import sys
from dataclasses import dataclass
from typing import Iterable

import numpy as np
import pandas as pd
import psycopg2
from psycopg2.extras import RealDictCursor, execute_values


# ---------------------------------------------------------------------------
# Configuración
# ---------------------------------------------------------------------------

from db_config import DB_CONFIG

# Según el DDL compartido, la tabla real de estadísticas partido/jugador es esta.
STATS_TABLE = "fixture_player_stats"
FIXTURE_TABLE = "fixture"
PLAYER_TABLE = "player"
TARGET_TABLE = "player_percentiles"

# league_id=0 queda reservado para el agregado global.
GLOBAL_LEAGUE_ID = 0

DEFAULT_MIN_MINUTES = 90
BATCH_SIZE = 5_000


@dataclass(frozen=True)
class Metric:
    value_col: str
    percentile_col: str


METRICS: tuple[Metric, ...] = (
    Metric("total_minutes", "pct_minutes"),
    Metric("avg_rating", "pct_rating"),
    Metric("goals_p90", "pct_goals_p90"),
    Metric("assists_p90", "pct_assists_p90"),
    Metric("shots_total_p90", "pct_shots_total_p90"),
    Metric("shots_on_p90", "pct_shots_on_p90"),
    Metric("passes_total_p90", "pct_passes_total_p90"),
    Metric("passes_key_p90", "pct_passes_key_p90"),
    Metric("pass_accuracy", "pct_pass_accuracy"),
    Metric("tackles_p90", "pct_tackles_p90"),
    Metric("interceptions_p90", "pct_interceptions_p90"),
    Metric("duels_won_pct", "pct_duels_won"),
    Metric("dribbles_success_pct", "pct_dribbles_success"),
    Metric("fouls_drawn_p90", "pct_fouls_drawn_p90"),
)

PCT_COLS = [m.percentile_col for m in METRICS]
OUTPUT_COLS = ["player_id", "index_id", "league_id", "season", *PCT_COLS]


# ---------------------------------------------------------------------------
# SQL
# ---------------------------------------------------------------------------

CREATE_TABLE_SQL = f"""
CREATE TABLE IF NOT EXISTS {TARGET_TABLE} (
    id                    BIGSERIAL   PRIMARY KEY,
    computed_at           TIMESTAMP(6),
    index_id              BIGINT,
    league_id             INTEGER     NOT NULL,
    pct_assists_p90       INTEGER,
    pct_dribbles_success  INTEGER,
    pct_duels_won         INTEGER,
    pct_fouls_drawn_p90   INTEGER,
    pct_goals_p90         INTEGER,
    pct_interceptions_p90 INTEGER,
    pct_minutes           INTEGER,
    pct_pass_accuracy     INTEGER,
    pct_passes_key_p90    INTEGER,
    pct_passes_total_p90  INTEGER,
    pct_rating            INTEGER,
    pct_shots_on_p90      INTEGER,
    pct_shots_total_p90   INTEGER,
    pct_tackles_p90       INTEGER,
    player_id             BIGINT      NOT NULL,
    season                VARCHAR(255) NOT NULL
)
"""

ENSURE_UNIQUE_INDEX_SQL = f"""
CREATE UNIQUE INDEX IF NOT EXISTS uq_player_percentiles
    ON {TARGET_TABLE} (player_id, league_id, season)
"""

ENSURE_INDEX_ID_INDEX_SQL = f"""
CREATE INDEX IF NOT EXISTS idx_pp_index_id
    ON {TARGET_TABLE} (index_id, season)
"""

ENSURE_PLAYER_SEASON_INDEX_SQL = f"""
CREATE INDEX IF NOT EXISTS idx_pp_player_season
    ON {TARGET_TABLE} (player_id, season)
"""

# Importante:
# - fps.player_id se une con p.index_id, no con p.id.
# - player_percentiles.player_id guarda p.id, porque tu FK/lógica interna debería apuntar a player.id.
# - index_id se guarda aparte para trazabilidad.
# - passes_accuracy del DDL es porcentaje, no número de pases completados. Por eso se pondera por passes_total.
LOAD_STATS_SQL = f"""
SELECT
    p.id                              AS player_id,
    p.index_id                        AS index_id,
    f.league_id                       AS league_id,
    f.season::text                    AS season,

    COALESCE(SUM(fps.minutes_played), 0)                AS total_minutes,
    AVG(fps.rating::float)                              AS avg_rating,
    COALESCE(SUM(fps.goals_scored), 0)                  AS total_goals,
    COALESCE(SUM(fps.assists), 0)                       AS total_assists,
    COALESCE(SUM(fps.shots_total), 0)                   AS total_shots_total,
    COALESCE(SUM(fps.shots_on), 0)                      AS total_shots_on,
    COALESCE(SUM(fps.passes_total), 0)                  AS total_passes_total,
    COALESCE(SUM(fps.passes_key), 0)                    AS total_passes_key,

    /* pases acertados estimados desde el porcentaje por partido */
    COALESCE(SUM(
        COALESCE(fps.passes_total, 0) * COALESCE(fps.passes_accuracy, 0) / 100.0
    ), 0)                                               AS total_passes_completed,

    COALESCE(SUM(fps.tackles_total), 0)                 AS total_tackles,
    COALESCE(SUM(fps.interceptions), 0)                 AS total_interceptions,
    COALESCE(SUM(fps.duels_total), 0)                   AS total_duels_total,
    COALESCE(SUM(fps.duels_won), 0)                     AS total_duels_won,
    COALESCE(SUM(fps.dribbles_att), 0)                  AS total_dribbles_att,
    COALESCE(SUM(fps.dribbles_suc), 0)                  AS total_dribbles_suc,
    COALESCE(SUM(fps.fouls_drawn), 0)                   AS total_fouls_drawn
FROM {STATS_TABLE} fps
JOIN {PLAYER_TABLE} p
  ON p.index_id = fps.player_id
JOIN {FIXTURE_TABLE} f
  ON f.id = fps.fixture_id
WHERE p.index_id IS NOT NULL
  AND (%(season)s IS NULL OR f.season::text = %(season)s)
GROUP BY p.id, p.index_id, f.league_id, f.season
"""

UPSERT_SQL = f"""
INSERT INTO {TARGET_TABLE}
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


# ---------------------------------------------------------------------------
# DB helpers
# ---------------------------------------------------------------------------

def get_connection():
    return psycopg2.connect(**DB_CONFIG, cursor_factory=RealDictCursor)


def ensure_target_table(conn) -> None:
    with conn.cursor() as cur:
        cur.execute(CREATE_TABLE_SQL)
        cur.execute(ENSURE_UNIQUE_INDEX_SQL)
        cur.execute(ENSURE_INDEX_ID_INDEX_SQL)
        cur.execute(ENSURE_PLAYER_SEASON_INDEX_SQL)
    conn.commit()


def truncate_target(conn, season: str | None) -> None:
    with conn.cursor() as cur:
        if season is None:
            cur.execute(f"TRUNCATE TABLE {TARGET_TABLE}")
        else:
            cur.execute(f"DELETE FROM {TARGET_TABLE} WHERE season = %s", (season,))
    conn.commit()


def load_stats(conn, season: str | None) -> pd.DataFrame:
    with conn.cursor() as cur:
        cur.execute(LOAD_STATS_SQL, {"season": season})
        rows = cur.fetchall()

    if not rows:
        return pd.DataFrame()

    df = pd.DataFrame([dict(r) for r in rows])

    numeric_cols = [
        "player_id", "index_id", "league_id", "total_minutes", "avg_rating",
        "total_goals", "total_assists", "total_shots_total", "total_shots_on",
        "total_passes_total", "total_passes_key", "total_passes_completed",
        "total_tackles", "total_interceptions", "total_duels_total",
        "total_duels_won", "total_dribbles_att", "total_dribbles_suc",
        "total_fouls_drawn",
    ]
    for col in numeric_cols:
        if col in df.columns:
            df[col] = pd.to_numeric(df[col], errors="coerce")

    df["season"] = df["season"].astype(str)
    return df


# ---------------------------------------------------------------------------
# Cálculo de métricas y percentiles
# ---------------------------------------------------------------------------

def compute_metric_values(df: pd.DataFrame) -> pd.DataFrame:
    """Añade columnas de valores base: p90 y porcentajes ponderados."""
    df = df.copy()
    mins = df["total_minutes"].replace(0, np.nan)

    df["goals_p90"] = df["total_goals"] * 90 / mins
    df["assists_p90"] = df["total_assists"] * 90 / mins
    df["shots_total_p90"] = df["total_shots_total"] * 90 / mins
    df["shots_on_p90"] = df["total_shots_on"] * 90 / mins
    df["passes_total_p90"] = df["total_passes_total"] * 90 / mins
    df["passes_key_p90"] = df["total_passes_key"] * 90 / mins
    df["tackles_p90"] = df["total_tackles"] * 90 / mins
    df["interceptions_p90"] = df["total_interceptions"] * 90 / mins
    df["fouls_drawn_p90"] = df["total_fouls_drawn"] * 90 / mins

    df["pass_accuracy"] = np.where(
        df["total_passes_total"] > 0,
        df["total_passes_completed"] / df["total_passes_total"] * 100,
        np.nan,
    )
    df["duels_won_pct"] = np.where(
        df["total_duels_total"] > 0,
        df["total_duels_won"] / df["total_duels_total"] * 100,
        np.nan,
    )
    df["dribbles_success_pct"] = np.where(
        df["total_dribbles_att"] > 0,
        df["total_dribbles_suc"] / df["total_dribbles_att"] * 100,
        np.nan,
    )
    return df


def add_percentiles(df: pd.DataFrame, group_cols: list[str]) -> pd.DataFrame:
    """Calcula percentiles 0-100 dentro de cada scope."""
    df = df.copy()

    for metric in METRICS:
        if metric.value_col not in df.columns:
            df[metric.percentile_col] = np.nan
            continue

        df[metric.percentile_col] = (
            df.groupby(group_cols, dropna=False)[metric.value_col]
              .transform(lambda s: s.rank(method="average", pct=True, na_option="keep") * 100)
              .round()
        )

    return df


def aggregate_global(league_totals: pd.DataFrame) -> pd.DataFrame:
    """Une todas las ligas de una misma temporada en una row global por jugador."""
    return (
        league_totals
        .groupby(["player_id", "index_id", "season"], as_index=False, dropna=False)
        .agg(
            total_minutes=("total_minutes", "sum"),
            # Mejor que una media simple por liga: pondera ratings por minutos.
            rating_weighted_sum=("rating_weighted_sum", "sum"),
            rating_minutes=("rating_minutes", "sum"),
            total_goals=("total_goals", "sum"),
            total_assists=("total_assists", "sum"),
            total_shots_total=("total_shots_total", "sum"),
            total_shots_on=("total_shots_on", "sum"),
            total_passes_total=("total_passes_total", "sum"),
            total_passes_key=("total_passes_key", "sum"),
            total_passes_completed=("total_passes_completed", "sum"),
            total_tackles=("total_tackles", "sum"),
            total_interceptions=("total_interceptions", "sum"),
            total_duels_total=("total_duels_total", "sum"),
            total_duels_won=("total_duels_won", "sum"),
            total_dribbles_att=("total_dribbles_att", "sum"),
            total_dribbles_suc=("total_dribbles_suc", "sum"),
            total_fouls_drawn=("total_fouls_drawn", "sum"),
        )
    )


def add_weighted_rating_helpers(df: pd.DataFrame) -> pd.DataFrame:
    """Prepara rating ponderado por minutos para agregados globales."""
    df = df.copy()
    rating = pd.to_numeric(df["avg_rating"], errors="coerce")
    minutes = pd.to_numeric(df["total_minutes"], errors="coerce").fillna(0)

    valid_rating = rating.notna() & (minutes > 0)
    df["rating_weighted_sum"] = np.where(valid_rating, rating * minutes, 0.0)
    df["rating_minutes"] = np.where(valid_rating, minutes, 0.0)
    return df


def finalize_weighted_rating(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()
    if "rating_weighted_sum" in df.columns and "rating_minutes" in df.columns:
        df["avg_rating"] = np.where(
            df["rating_minutes"] > 0,
            df["rating_weighted_sum"] / df["rating_minutes"],
            np.nan,
        )
    return df


def build_scopes(raw_league_totals: pd.DataFrame, min_minutes: int) -> pd.DataFrame:
    """
    Construye:
    - Scope liga: jugador/liga/temporada.
    - Scope global: jugador/temporada con league_id=0.

    El filtro de minutos se aplica por scope:
    - Para liga: minutos del jugador en esa liga+temporada.
    - Para global: minutos totales del jugador en todas las ligas de esa temporada.
    """
    base = add_weighted_rating_helpers(raw_league_totals)

    # Scope liga.
    league_scope = base[base["total_minutes"] >= min_minutes].copy()
    league_scope = finalize_weighted_rating(league_scope)
    league_scope = compute_metric_values(league_scope)
    league_scope = add_percentiles(league_scope, ["league_id", "season"])

    # Scope global: no uses el df ya filtrado por liga; si no, perderás jugadores
    # que acumulan minutos suficientes entre varias competiciones.
    global_scope = aggregate_global(base)
    global_scope = finalize_weighted_rating(global_scope)
    global_scope = global_scope[global_scope["total_minutes"] >= min_minutes].copy()
    global_scope["league_id"] = GLOBAL_LEAGUE_ID
    global_scope = compute_metric_values(global_scope)
    global_scope = add_percentiles(global_scope, ["season"])

    combined = pd.concat(
        [global_scope[OUTPUT_COLS], league_scope[OUTPUT_COLS]],
        ignore_index=True,
    )

    # Seguridad: si por datos raros aparece más de una row para la misma clave,
    # nos quedamos con la última y lo reportaremos por consola.
    before = len(combined)
    combined = combined.drop_duplicates(["player_id", "league_id", "season"], keep="last")
    after = len(combined)
    if before != after:
        print(f"AVISO: eliminadas {before - after} filas duplicadas por player_id/league_id/season")

    return combined


# ---------------------------------------------------------------------------
# Persistencia
# ---------------------------------------------------------------------------

def to_nullable_bigint(value) -> int | None:
    """Convierte IDs enteros permitiendo valores mayores de 100."""
    if value is None:
        return None
    try:
        f = float(value)
    except (TypeError, ValueError):
        return None
    if np.isnan(f):
        return None
    return int(round(f))


def to_nullable_percentile(value) -> int | None:
    """Convierte percentiles al rango esperado 0-100."""
    if value is None:
        return None
    try:
        f = float(value)
    except (TypeError, ValueError):
        return None
    if np.isnan(f):
        return None
    return max(0, min(100, int(round(f))))


def build_upsert_rows(df: pd.DataFrame) -> list[tuple]:
    now = dt.datetime.now()
    rows: list[tuple] = []

    ordered_pct_cols = [
        "pct_minutes",
        "pct_rating",
        "pct_goals_p90",
        "pct_assists_p90",
        "pct_shots_total_p90",
        "pct_shots_on_p90",
        "pct_passes_total_p90",
        "pct_passes_key_p90",
        "pct_pass_accuracy",
        "pct_tackles_p90",
        "pct_interceptions_p90",
        "pct_duels_won",
        "pct_dribbles_success",
        "pct_fouls_drawn_p90",
    ]

    for _, r in df.iterrows():
        rows.append((
            int(r["player_id"]),
            to_nullable_bigint(r.get("index_id")),
            int(r["league_id"]),
            str(r["season"]),
            *[to_nullable_percentile(r.get(col)) for col in ordered_pct_cols],
            now,
        ))

    return rows


def upsert_rows(conn, rows: list[tuple]) -> int:
    if not rows:
        return 0

    total = 0
    for i in range(0, len(rows), BATCH_SIZE):
        batch = rows[i:i + BATCH_SIZE]
        with conn.cursor() as cur:
            execute_values(cur, UPSERT_SQL, batch, page_size=BATCH_SIZE)
        conn.commit()
        total += len(batch)

    return total


# ---------------------------------------------------------------------------
# Diagnóstico opcional
# ---------------------------------------------------------------------------

def print_diagnostics(raw: pd.DataFrame, final: pd.DataFrame, min_minutes: int) -> None:
    print("\nDiagnóstico:")
    print(f"  Raw liga: {len(raw):,} filas jugador/liga/temporada")
    print(f"  Raw liga con >={min_minutes} min: {(raw['total_minutes'] >= min_minutes).sum():,}")

    global_counts = (
        raw.groupby(["player_id", "season"], as_index=False, dropna=False)["total_minutes"]
           .sum()
    )
    print(f"  Global jugador/temporada: {len(global_counts):,}")
    print(f"  Global jugador/temporada con >={min_minutes} min: {(global_counts['total_minutes'] >= min_minutes).sum():,}")

    print(f"  Final a persistir: {len(final):,}")
    if not final.empty:
        print(f"    - Global league_id=0: {(final['league_id'] == GLOBAL_LEAGUE_ID).sum():,}")
        print(f"    - Liga: {(final['league_id'] != GLOBAL_LEAGUE_ID).sum():,}")
        print("  Temporadas:", ", ".join(sorted(final["season"].dropna().unique().astype(str))))


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def run(season: str | None = None, min_minutes: int = DEFAULT_MIN_MINUTES, truncate: bool = False) -> int:
    conn = get_connection()
    try:
        ensure_target_table(conn)

        if truncate:
            print("Limpiando player_percentiles" + (f" para season={season}" if season else "") + "...")
            truncate_target(conn, season)

        print("Cargando estadísticas...")
        raw = load_stats(conn, season)
        if raw.empty:
            print("No hay estadísticas para calcular percentiles.")
            return 0

        print("Calculando scopes de liga y global...")
        final = build_scopes(raw, min_minutes)
        print_diagnostics(raw, final, min_minutes)

        rows = build_upsert_rows(final)
        print(f"\nPersistiendo {len(rows):,} filas en {TARGET_TABLE}...")
        written = upsert_rows(conn, rows)
        print(f"OK: {written:,} filas insertadas/actualizadas.")
        return written
    finally:
        conn.close()


def parse_args(argv: Iterable[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Calcula percentiles de jugadores y actualiza player_percentiles")
    parser.add_argument("--season", default=None, help="Temporada concreta, por ejemplo 2024")
    parser.add_argument("--min-minutes", type=int, default=DEFAULT_MIN_MINUTES, help="Mínimo de minutos por scope")
    parser.add_argument("--truncate", action="store_true", help="Borra player_percentiles antes de recalcular; si hay --season, borra solo esa season")
    return parser.parse_args(list(argv))


def main(argv: Iterable[str] | None = None) -> int:
    args = parse_args(argv if argv is not None else sys.argv[1:])
    try:
        run(season=args.season, min_minutes=args.min_minutes, truncate=args.truncate)
        return 0
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
