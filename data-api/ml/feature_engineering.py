"""
Feature engineering para el modelo predictivo de partidos.

Genera un dataset de entrenamiento a partir de PostgreSQL con:
  - Rolling stats de los últimos N partidos por equipo (forma reciente)
  - Split home/away performance
  - Días de descanso entre partidos
  - Estadísticas H2H de los últimos M enfrentamientos
  - Variables objetivo: result (1X2), over25, btts, total_goals

Uso:
    python feature_engineering.py
    python feature_engineering.py --lookback 7 --h2h 8 --output dataset.parquet
"""

import argparse
import sys
from pathlib import Path

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
            ts.goalkeeper_saves
        FROM fixture_team_stats ts
        JOIN fixture f ON f.id = ts.fixture_id
        WHERE f.status_short = 'FT'
    """
    with conn.cursor() as cur:
        cur.execute(query)
        rows = cur.fetchall()
    return pd.DataFrame([dict(r) for r in rows])


# ---------------------------------------------------------------------------
# Construcción del historial por equipo
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
        for venue, team_id, g_for, g_against in [
            ("H", f.home_team_id, f.goals_home, f.goals_away),
            ("A", f.away_team_id, f.goals_away, f.goals_home),
        ]:
            ts = ts_idx.get((f.id, team_id), {})
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
                "xg_for":        ts.get("expected_goals"),
                "shots_on_goal": ts.get("shots_on_goal"),
                "shots_total":   ts.get("shots_total"),
                "possession":    ts.get("ball_possession"),
                "passes_pct":    ts.get("passes_pct"),
                "corner_kicks":  ts.get("corner_kicks"),
                "saves":         ts.get("goalkeeper_saves"),
            })

    df = pd.DataFrame(records)
    df = df.sort_values(["team_id", "match_date"]).reset_index(drop=True)
    return df


# ---------------------------------------------------------------------------
# Rolling features
# ---------------------------------------------------------------------------

ROLL_COLS = [
    "goals_for", "goals_against", "won", "drew", "lost",
    "xg_for", "shots_on_goal", "shots_total",
    "possession", "passes_pct", "corner_kicks", "saves",
]


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
# H2H features
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
    # para evitar que el pd.concat haya metido columnas _h_ en filas A y viceversa
    h_cols = [c for c in rolling_venue.columns if f"_h_last{n}" in c]
    a_cols = [c for c in rolling_venue.columns if f"_a_last{n}" in c]

    home_venue = _fid(
        rolling_venue[rolling_venue["venue"] == "H"][["fixture_id", "team_id"] + h_cols].copy()
    ).rename(columns={"team_id": "home_team_id"})

    away_venue = _fid(
        rolling_venue[rolling_venue["venue"] == "A"][["fixture_id", "team_id"] + a_cols].copy()
    ).rename(columns={"team_id": "away_team_id"})

    # Days rest — copy() para no mutar el df base en ambos renombrados
    rest_norm = _fid(rest)
    home_rest = rest_norm.copy().rename(columns={"team_id": "home_team_id", "days_rest": "home_days_rest"})
    away_rest = rest_norm.copy().rename(columns={"team_id": "away_team_id", "days_rest": "away_days_rest"})

    # Season form — separar home/away con renombrado explícito
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

    # Renombrar fixture.id → fixture_id para claridad
    df = df.rename(columns={"id": "fixture_id"})

    # Features diferenciales home-minus-away
    # Capturan la ventaja relativa de forma mejor que las absolutas por separado
    diff_pairs = [
        (f"home_roll_goals_for_last{n}",      f"away_roll_goals_for_last{n}",      "diff_goals_for"),
        (f"home_roll_goals_against_last{n}",  f"away_roll_goals_against_last{n}",  "diff_goals_against"),
        (f"home_roll_won_last{n}",            f"away_roll_won_last{n}",            "diff_wins"),
        (f"home_roll_shots_on_goal_last{n}",  f"away_roll_shots_on_goal_last{n}",  "diff_shots_on_goal"),
        (f"home_roll_possession_last{n}",     f"away_roll_possession_last{n}",     "diff_possession"),
        (f"home_roll_passes_pct_last{n}",     f"away_roll_passes_pct_last{n}",     "diff_passes_pct"),
        (f"home_roll_xg_for_last{n}",         f"away_roll_xg_for_last{n}",         "diff_xg"),
        ("home_season_ppg",                   "away_season_ppg",                   "diff_season_ppg"),
        ("home_season_gfpg",                  "away_season_gfpg",                  "diff_season_gfpg"),
        ("home_season_gapg",                  "away_season_gapg",                  "diff_season_gapg"),
    ]
    for col_h, col_a, name in diff_pairs:
        if col_h in df.columns and col_a in df.columns:
            df[name] = df[col_h] - df[col_a]

    return df


# ---------------------------------------------------------------------------
# Diagnóstico de completitud
# ---------------------------------------------------------------------------

def print_diagnostics(fixtures: pd.DataFrame, dataset: pd.DataFrame, n: int) -> None:
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

    print(f"\n  Completitud de features clave (nulos):")
    checks = {
        f"home_roll_xg_for_last{n}":        "xG for (home)",
        f"home_roll_shots_on_goal_last{n}":  "Shots on goal (home)",
        f"home_roll_possession_last{n}":     "Possession (home)",
        f"home_roll_goals_for_last{n}":      "Goals for (home)",
        f"h2h_avg_goals":                    "H2H avg goals",
        "home_days_rest":                    "Days rest (home)",
    }
    for col, label in checks.items():
        if col in dataset.columns:
            null_pct = dataset[col].isna().mean()
            flag = "  ⚠" if null_pct > 0.4 else "  ✓"
            print(f"  {flag}  {label:<28} {null_pct:.1%} nulos")
        else:
            print(f"       {label:<28} columna no encontrada")

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

    print("Cargando fixtures y estadísticas...")
    fixtures   = load_fixtures(conn)
    team_stats = load_team_stats(conn)
    conn.close()
    print(f"  → {len(fixtures)} fixtures FT  |  {len(team_stats)} registros team_stats")

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

    print("Ensamblando dataset...")
    dataset = assemble_dataset(fixtures, rolling, rolling_venue, rest, h2h, season_form, args.lookback)

    print_diagnostics(fixtures, dataset, args.lookback)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    dataset.to_parquet(args.output, index=False)
    print(f"Dataset guardado en: {args.output}")
    print(f"Siguiente paso → Fase 3: train_model.py")


if __name__ == "__main__":
    main()
