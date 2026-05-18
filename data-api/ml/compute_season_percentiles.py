"""
Pre-computa percentiles temporales PARA EL MODELO PREDICTIVO INCREMENTAL.

La tabla `player_season_percentiles` almacena, para cada (player_id, league_id,
season, as_of_date), el rango percentil del jugador calculado con SOLO los
partidos anteriores a as_of_date en esa liga+temporada.

IMPORTANTE: Esta es la ÚNICA tabla que se llena aquí.
Los percentiles para el FRONTEND se calculan aparte en GeneratePlayerPercentilesFrontend.java

Esto elimina el leakage del modelo y convierte la consulta de predicción en
un lookup O(log n) en lugar de una CTE de ventana sobre miles de filas.

Uso:
    python compute_percentiles.py              # procesar todo desde cero
    python compute_percentiles.py --incremental  # solo fechas nuevas (no procesadas)
    python compute_percentiles.py --since 2025   # solo temporada 2025+
"""

import argparse
import sys
from pathlib import Path

import numpy as np
import pandas as pd
import psycopg2
from psycopg2.extras import RealDictCursor, execute_values

from db_config import DB_CONFIG

MIN_MINUTES = 90
BATCH_SIZE  = 5_000  # filas por INSERT


# ---------------------------------------------------------------------------
# DDL
# ---------------------------------------------------------------------------

CREATE_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS player_season_percentiles (
    player_id       INTEGER     NOT NULL,
    league_id       INTEGER     NOT NULL,
    season          INTEGER     NOT NULL,
    as_of_date      TIMESTAMPTZ NOT NULL,
    goals_p90_pct   FLOAT,
    kp_p90_pct      FLOAT,
    def_p90_pct     FLOAT,
    avg_rating_pct  FLOAT,
    PRIMARY KEY (player_id, league_id, season, as_of_date)
);

CREATE INDEX IF NOT EXISTS idx_psp_lookup
    ON player_season_percentiles (player_id, league_id, season, as_of_date DESC);
"""


# ---------------------------------------------------------------------------
# DB helpers
# ---------------------------------------------------------------------------

def get_connection():
    return psycopg2.connect(**DB_CONFIG, cursor_factory=RealDictCursor)


def ensure_table(conn) -> None:
    with conn.cursor() as cur:
        cur.execute(CREATE_TABLE_SQL)
    conn.commit()
    print("  ✓ Tabla player_season_percentiles lista")


def load_processed_dates(conn) -> set:
    """Devuelve el conjunto de (league_id, season, as_of_date) ya procesados."""
    with conn.cursor() as cur:
        cur.execute("""
            SELECT DISTINCT league_id, season, as_of_date
            FROM player_season_percentiles
        """)
        return {(r["league_id"], r["season"], r["as_of_date"]) for r in cur.fetchall()}


# ---------------------------------------------------------------------------
# Carga de datos
# ---------------------------------------------------------------------------

def load_fixtures(conn, since_season: int | None = None) -> pd.DataFrame:
    season_filter = f"AND f.season >= {since_season}" if since_season else ""
    with conn.cursor() as cur:
        cur.execute(f"""
            SELECT f.id AS fixture_id, f.league_id, f.season, f.match_date
            FROM fixture f
            WHERE f.status_short = 'FT'
            {season_filter}
            ORDER BY f.match_date
        """)
        df = pd.DataFrame([dict(r) for r in cur.fetchall()])
    df["match_date"] = pd.to_datetime(df["match_date"], utc=True)
    return df


def load_player_stats(conn, since_season: int | None = None) -> pd.DataFrame:
    season_filter = f"AND f.season >= {since_season}" if since_season else ""
    with conn.cursor() as cur:
        cur.execute(f"""
            SELECT
                ps.fixture_id,
                ps.player_id,
                ps.minutes_played,
                ps.goals_scored,
                ps.passes_key,
                ps.tackles_total,
                ps.interceptions,
                ps.rating::float AS rating
            FROM fixture_player_stats ps
            JOIN fixture f ON f.id = ps.fixture_id
            WHERE f.status_short = 'FT'
              AND ps.minutes_played > 0
            {season_filter}
        """)
        df = pd.DataFrame([dict(r) for r in cur.fetchall()])
    df["rating"] = pd.to_numeric(df["rating"], errors="coerce")
    return df


# ---------------------------------------------------------------------------
# Cómputo temporal (mismo algoritmo que feature_engineering.py)
# ---------------------------------------------------------------------------

def compute_temporal_percentiles(
    fixtures: pd.DataFrame,
    player_stats: pd.DataFrame,
    processed_dates: set | None = None,
) -> pd.DataFrame:
    """
    Para cada (league_id, season), itera fixtures en orden cronológico.
    Antes de cada jornada, calcula PERCENT_RANK de todos los jugadores con
    suficientes minutos acumulados hasta ese momento.

    Retorna: player_id, league_id, season, as_of_date, goals_p90_pct, kp_p90_pct, def_p90_pct, avg_rating_pct
    """
    ps = player_stats.merge(
        fixtures[["fixture_id", "league_id", "season", "match_date"]],
        on="fixture_id", how="left",
    )

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

        cum: dict = {}

        for _, drow in date_groups.iterrows():
            fdate = drow["match_date"]

            # Si es incremental, saltar fechas ya procesadas
            if processed_dates and (int(league_id), int(season), fdate) in processed_dates:
                # Aún hay que actualizar el acumulado para las fechas siguientes
                date_data = lg[lg["fixture_id"].isin(drow["fixture_id"])]
                _update_cum(cum, date_data)
                continue

            # snapshot ANTES de los fixtures de este día
            if cum:
                snap_rows = _build_snapshot(cum)
                if len(snap_rows) >= 2:
                    snap = _rank_snapshot(snap_rows)
                    snap["league_id"]  = int(league_id)
                    snap["season"]     = int(season)
                    snap["as_of_date"] = fdate
                    results.append(
                        snap[["player_id", "league_id", "season", "as_of_date",
                              "goals_p90_pct", "kp_p90_pct",
                              "def_p90_pct",   "avg_rating_pct"]]
                    )

            # actualizar acumulado
            date_data = lg[lg["fixture_id"].isin(drow["fixture_id"])]
            _update_cum(cum, date_data)

    if not results:
        return pd.DataFrame()
    return pd.concat(results, ignore_index=True)


def _build_snapshot(cum: dict) -> list[dict]:
    return [
        {
            "player_id":     pid,
            "total_minutes": d["minutes"],
            "total_goals":   d["goals"],
            "total_kp":      d["kp"],
            "total_def":     d["tackles"] + d["int_"],
            "avg_rating":    d["sum_r"] / d["n_r"] if d["n_r"] > 0 else np.nan,
        }
        for pid, d in cum.items()
        if d["minutes"] >= MIN_MINUTES
    ]


def _rank_snapshot(snap_rows: list[dict]) -> pd.DataFrame:
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
    return snap


def _si(v, default: int = 0) -> int:
    """Convierte a int de forma segura — maneja None y NaN."""
    if v is None or (isinstance(v, float) and np.isnan(v)):
        return default
    return int(v)


def _update_cum(cum: dict, date_data: pd.DataFrame) -> None:
    for _, prow in date_data.iterrows():
        pid = int(prow["player_id"])
        if pid not in cum:
            cum[pid] = {"minutes": 0, "goals": 0, "kp": 0,
                        "tackles": 0, "int_": 0, "sum_r": 0.0, "n_r": 0}
        cum[pid]["minutes"]  += _si(prow.get("minutes_played"))
        cum[pid]["goals"]    += _si(prow.get("goals_scored"))
        cum[pid]["kp"]       += _si(prow.get("passes_key"))
        cum[pid]["tackles"]  += _si(prow.get("tackles_total"))
        cum[pid]["int_"]     += _si(prow.get("interceptions"))
        r = prow.get("rating")
        if r is not None and not pd.isna(r):
            cum[pid]["sum_r"] += float(r)
            cum[pid]["n_r"]   += 1


# ---------------------------------------------------------------------------
# Persistencia
# ---------------------------------------------------------------------------

def upsert_to_db(conn, df: pd.DataFrame) -> int:
    if df.empty:
        return 0

    sql = """
        INSERT INTO player_season_percentiles
            (player_id, league_id, season, as_of_date,
             goals_p90_pct, kp_p90_pct, def_p90_pct, avg_rating_pct)
        VALUES %s
        ON CONFLICT (player_id, league_id, season, as_of_date) DO UPDATE SET
            goals_p90_pct  = EXCLUDED.goals_p90_pct,
            kp_p90_pct     = EXCLUDED.kp_p90_pct,
            def_p90_pct    = EXCLUDED.def_p90_pct,
            avg_rating_pct = EXCLUDED.avg_rating_pct
    """

    def _to_float(v):
        return None if v is None or (isinstance(v, float) and np.isnan(v)) else float(v)

    rows = [
        (
            int(r["player_id"]),
            int(r["league_id"]),
            int(r["season"]),
            r["as_of_date"],
            _to_float(r["goals_p90_pct"]),
            _to_float(r["kp_p90_pct"]),
            _to_float(r["def_p90_pct"]),
            _to_float(r["avg_rating_pct"]),
        )
        for _, r in df.iterrows()
    ]

    inserted = 0
    for i in range(0, len(rows), BATCH_SIZE):
        batch = rows[i : i + BATCH_SIZE]
        with conn.cursor() as cur:
            execute_values(cur, sql, batch, page_size=BATCH_SIZE)
        conn.commit()
        inserted += len(batch)

    return inserted


# ---------------------------------------------------------------------------
# Entrypoint
# ---------------------------------------------------------------------------

def parse_args():
    p = argparse.ArgumentParser()
    p.add_argument("--incremental", action="store_true",
                   help="Procesar solo fechas no presentes en la tabla")
    p.add_argument("--since", type=int, default=None,
                   help="Procesar solo desde esta temporada (ej: 2025)")
    return p.parse_args()


def main():
    args = parse_args()

    print("Conectando a PostgreSQL...")
    try:
        conn = get_connection()
    except Exception as e:
        print(f"ERROR de conexión: {e}")
        sys.exit(1)

    ensure_table(conn)

    processed_dates = None
    if args.incremental:
        print("Modo incremental: cargando fechas ya procesadas...")
        processed_dates = load_processed_dates(conn)
        print(f"  → {len(processed_dates)} (league, season, date) ya procesados")

    print("Cargando datos de fixtures y jugadores...")
    fixtures     = load_fixtures(conn, since_season=args.since)
    player_stats = load_player_stats(conn, since_season=args.since)
    print(f"  → {len(fixtures)} fixtures  |  {len(player_stats)} registros de jugadores")

    if fixtures.empty or player_stats.empty:
        print("Sin datos suficientes para computar percentiles.")
        conn.close()
        return

    print("Computando percentiles temporales (puede tardar unos minutos)...")
    pct_df = compute_temporal_percentiles(fixtures, player_stats, processed_dates)
    print(f"  → {len(pct_df)} filas computadas")

    if pct_df.empty:
        print("Sin filas nuevas que insertar.")
        conn.close()
        return

    print("Insertando en player_season_percentiles...")
    n = upsert_to_db(conn, pct_df)
    print(f"  → {n} filas insertadas/actualizadas")

    conn.close()
    print("Completado.")


if __name__ == "__main__":
    main()
