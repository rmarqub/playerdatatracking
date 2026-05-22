"""
API de valoración de mercado de jugadores.

Calcula el valor de mercado estimado (0–200 M €) basándose en:
  - Percentiles de rendimiento (tabla player_percentiles) ponderados por posición
  - Factor de edad (curva asimétrica, peak 24-27)
  - Factor de tier de liga (tabla league_tier)
  - Factor de participación (minutos jugados)
  - Penalización por lesión

Uso:
    uvicorn valuation_api:app --host 127.0.0.1 --port 8002 --reload
"""

from __future__ import annotations

from contextlib import asynccontextmanager
from typing import Optional

import psycopg2
from fastapi import FastAPI, HTTPException
from psycopg2.extras import RealDictCursor
from pydantic import BaseModel

from db_config import DB_CONFIG

# ---------------------------------------------------------------------------
# Constantes del modelo
# ---------------------------------------------------------------------------

MAX_VALUE = 200_000_000  # Valor máximo en euros

# Curva de edad: factor multiplicativo según la edad del jugador
AGE_CURVE: list[tuple[int, float]] = [
    (18,  0.55),
    (21,  0.72),
    (23,  0.88),
    (27,  1.00),
    (29,  0.92),
    (31,  0.75),
    (33,  0.55),
    (35,  0.35),
    (999, 0.18),
]

# Factor de participación según el percentil de minutos
MINUTES_FACTOR: list[tuple[int, float]] = [
    (19,  0.40),
    (39,  0.60),
    (59,  0.80),
    (100, 1.00),
]

# Ponderaciones de métricas de percentil por posición
WEIGHTS: dict[str, dict[str, float]] = {
    "G": {
        "pct_rating":          0.45,
        "pct_minutes":         0.30,
        "pct_pass_accuracy":   0.25,
    },
    "D": {
        "pct_rating":            0.25,
        "pct_tackles_p90":       0.25,
        "pct_interceptions_p90": 0.20,
        "pct_duels_won":         0.15,
        "pct_passes_total_p90":  0.15,
    },
    "M": {
        "pct_rating":           0.20,
        "pct_passes_key_p90":   0.20,
        "pct_passes_total_p90": 0.15,
        "pct_assists_p90":      0.15,
        "pct_goals_p90":        0.10,
        "pct_tackles_p90":      0.10,
        "pct_dribbles_success": 0.10,
    },
    "F": {
        "pct_goals_p90":        0.35,
        "pct_rating":           0.20,
        "pct_assists_p90":      0.15,
        "pct_shots_on_p90":     0.15,
        "pct_dribbles_success": 0.10,
        "pct_passes_key_p90":   0.05,
    },
}

DEFAULT_TIER_FACTOR = 0.40


# ---------------------------------------------------------------------------
# Arranque / lifespan
# ---------------------------------------------------------------------------

@asynccontextmanager
async def lifespan(app: FastAPI):
    _test_db_connection()
    print("[ValuationAPI] Conexión a BD verificada.")
    yield


app = FastAPI(title="Player Valuation API", version="1.0", lifespan=lifespan)


# ---------------------------------------------------------------------------
# Schemas de respuesta
# ---------------------------------------------------------------------------

class MarketValueResponse(BaseModel):
    index_id: int
    player_name: str
    age: Optional[int]
    position: Optional[str]
    injured: bool
    team_name: Optional[str]
    league_name: Optional[str]
    league_tier: int
    tier_factor: float
    performance_score: float
    age_factor: float
    minutes_factor: float
    injury_penalty: float
    market_value: int
    market_value_fmt: str
    season: Optional[str]
    note: Optional[str]


# ---------------------------------------------------------------------------
# DB helpers
# ---------------------------------------------------------------------------

def _get_conn():
    cfg = DB_CONFIG
    return psycopg2.connect(
        host=cfg["host"],
        port=cfg["port"],
        dbname=cfg["dbname"],
        user=cfg["user"],
        password=cfg["password"],
        options="-c lc_messages=C",
        cursor_factory=RealDictCursor,
    )


def _test_db_connection():
    conn = _get_conn()
    conn.close()


# ---------------------------------------------------------------------------
# Queries
# ---------------------------------------------------------------------------

def _fetch_player(conn, index_id: int) -> dict:
    with conn.cursor() as cur:
        cur.execute("""
            SELECT
                p.id           AS player_id,
                p.index_id,
                p.firstname,
                p.lastname,
                p.fullname,
                p.age,
                p.injured,
                p.team         AS team_id,
                c.nombre       AS team_name
            FROM player p
            LEFT JOIN club c ON c.id = p.team
            WHERE p.index_id = %s
            ORDER BY p.id DESC
            LIMIT 1
        """, (index_id,))
        row = cur.fetchone()
    if not row:
        raise HTTPException(status_code=404, detail=f"Jugador con index_id={index_id} no encontrado")
    return dict(row)


def _fetch_dominant_position(conn, index_id: int) -> Optional[str]:
    """Posición más frecuente del jugador como titular (no suplente)."""
    with conn.cursor() as cur:
        cur.execute("""
            SELECT position, COUNT(*) AS cnt
            FROM fixture_player_stats
            WHERE player_id = %s
              AND position IS NOT NULL
              AND substitute = false
            GROUP BY position
            ORDER BY cnt DESC
            LIMIT 1
        """, (index_id,))
        row = cur.fetchone()
    return row["position"] if row else None


def _fetch_percentiles(conn, index_id: int) -> tuple[Optional[dict], Optional[str]]:
    """
    Busca los percentiles más recientes del jugador por index_id.
    Prioriza league_id=0 (global) de la temporada más reciente.
    """
    with conn.cursor() as cur:
        # Global primero
        cur.execute("""
            SELECT *
            FROM player_percentiles
            WHERE index_id = %s AND league_id = 0
            ORDER BY season DESC
            LIMIT 1
        """, (index_id,))
        row = cur.fetchone()
        if row:
            return dict(row), str(row["season"])

        # Si no hay global, cualquier liga disponible
        cur.execute("""
            SELECT *
            FROM player_percentiles
            WHERE index_id = %s
            ORDER BY season DESC, league_id
            LIMIT 1
        """, (index_id,))
        row = cur.fetchone()
        if row:
            return dict(row), str(row["season"])

    return None, None


def _fetch_league_tier(conn, team_id: Optional[int]) -> tuple[int, float, Optional[str]]:
    """
    Obtiene el tier y factor de la mejor liga en la que juega el equipo del jugador.
    'Mejor' = tier más bajo distinto de 0 (tier 1 = élite).
    """
    if not team_id:
        return 0, DEFAULT_TIER_FACTOR, None

    with conn.cursor() as cur:
        cur.execute("""
            SELECT
                lt.tier,
                lt.tier_factor,
                t.name AS league_name
            FROM club_in_league cil
            JOIN torneo t  ON t.id  = cil.torneo
            JOIN league_tier lt ON lt.torneo_id = cil.torneo
            WHERE cil.club = %s
              AND lt.tier > 0
            ORDER BY lt.tier ASC
            LIMIT 1
        """, (team_id,))
        row = cur.fetchone()

    if row:
        return row["tier"], row["tier_factor"], row["league_name"]

    # Fallback: busca sin filtro de tier (puede ser 0 = sin asignar)
    with conn.cursor() as cur:
        cur.execute("""
            SELECT
                COALESCE(lt.tier, 0)        AS tier,
                COALESCE(lt.tier_factor, %s) AS tier_factor,
                t.name AS league_name
            FROM club_in_league cil
            JOIN torneo t ON t.id = cil.torneo
            LEFT JOIN league_tier lt ON lt.torneo_id = cil.torneo
            WHERE cil.club = %s
            LIMIT 1
        """, (DEFAULT_TIER_FACTOR, team_id))
        row = cur.fetchone()

    if row:
        return row["tier"], float(row["tier_factor"]), row["league_name"]

    return 0, DEFAULT_TIER_FACTOR, None


# ---------------------------------------------------------------------------
# Cálculo del valor
# ---------------------------------------------------------------------------

def _age_factor(age: Optional[int]) -> float:
    if age is None:
        return 0.70  # desconocida → conservador
    for max_age, factor in AGE_CURVE:
        if age <= max_age:
            return factor
    return 0.18


def _minutes_factor(pct_minutes: Optional[int]) -> float:
    if pct_minutes is None:
        return 0.40
    for threshold, factor in MINUTES_FACTOR:
        if pct_minutes <= threshold:
            return factor
    return 1.00


def _performance_score(pcts: dict, position: str) -> float:
    """
    Calcula un score 0-100 como media ponderada de percentiles según posición.
    Si alguna métrica falta, se distribuye su peso entre las demás.
    """
    pos_key = position if position in WEIGHTS else "M"
    weights = WEIGHTS[pos_key]

    total_w = 0.0
    weighted_sum = 0.0
    for metric, w in weights.items():
        val = pcts.get(metric)
        if val is not None:
            weighted_sum += val * w
            total_w += w

    if total_w == 0:
        return 0.0
    return weighted_sum / total_w  # reescala a la suma de pesos disponibles


def _format_value(v: int) -> str:
    if v >= 1_000_000:
        return f"€{v/1_000_000:.1f}M"
    if v >= 1_000:
        return f"€{v/1_000:.0f}K"
    return f"€{v}"


def _calculate(
    player: dict,
    position: Optional[str],
    pcts: Optional[dict],
    tier: int,
    tier_factor: float,
    season: Optional[str],
) -> dict:
    age           = player.get("age")
    injured       = bool(player.get("injured"))
    pos           = position or "M"

    age_f     = _age_factor(age)
    min_f     = _minutes_factor(pcts.get("pct_minutes") if pcts else None)
    inj_pen   = 0.80 if injured else 1.0
    perf_s    = _performance_score(pcts or {}, pos)

    raw_value = MAX_VALUE * ((perf_s / 100) ** 2) * age_f * tier_factor * min_f * inj_pen
    market_v  = max(0, min(MAX_VALUE, int(round(raw_value))))

    note = None
    if pcts is None:
        note = "Sin percentiles calculados — el valor puede estar subestimado"

    fullname = (player.get("fullname")
                or f"{player.get('firstname', '')} {player.get('lastname', '')}".strip()
                or "Desconocido")

    return {
        "index_id":          player["index_id"],
        "player_name":       fullname,
        "age":               age,
        "position":          pos,
        "injured":           injured,
        "team_name":         player.get("team_name"),
        "league_tier":       tier,
        "tier_factor":       round(tier_factor, 2),
        "performance_score": round(perf_s, 2),
        "age_factor":        round(age_f, 2),
        "minutes_factor":    round(min_f, 2),
        "injury_penalty":    round(inj_pen, 2),
        "market_value":      market_v,
        "market_value_fmt":  _format_value(market_v),
        "season":            season,
        "note":              note,
    }


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------

@app.get("/health")
def health():
    return {"status": "ok", "service": "valuation-api"}


@app.get("/player-value/{index_id}", response_model=MarketValueResponse)
def get_player_value(index_id: int):
    conn = _get_conn()
    try:
        player   = _fetch_player(conn, index_id)
        position = _fetch_dominant_position(conn, index_id)
        pcts, season = _fetch_percentiles(conn, index_id)
        tier, tier_factor, league_name = _fetch_league_tier(conn, player.get("team_id"))

        result = _calculate(player, position, pcts, tier, tier_factor, season)
        result["league_name"] = league_name
        return result
    finally:
        conn.close()
