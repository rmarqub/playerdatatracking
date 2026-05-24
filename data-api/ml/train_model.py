"""
Fase 3: Entrenamiento de modelos LightGBM para predicción de partidos — v3.

Cambios respecto a v2:
  - Sin límite de features por defecto (--max-features 0): incluye todas las features
    disponibles, incluyendo las específicas de empate que v2 descartaba en selección.
  - DRAW_PINNED_FEATURES: si se activa --max-features, las features de draw están
    protegidas y siempre se incluyen independientemente de su importancia global.
  - class_weight del empate: 1.12 → 1.22 para mejorar discriminación sin sobredisparo.
  - Mantiene: temperature scaling, corrección de prior, regularización fuerte de v2.

Uso:
    python train_model_v3.py --input training_data.parquet
    python train_model_v3.py --input training_data.parquet --test-seasons 2025
    python train_model_v3.py --input training_data.parquet --max-features 100
"""

import argparse
import pickle
import sys
import warnings
from pathlib import Path
from typing import Optional

import lightgbm as lgb
import numpy as np
import pandas as pd
from scipy.stats import poisson as scipy_poisson
from sklearn.metrics import (
    accuracy_score,
    log_loss,
    roc_auc_score,
    brier_score_loss,
)

warnings.filterwarnings("ignore", category=UserWarning)

INPUT_FILE = Path(__file__).parent / "training_data.parquet"
MODELS_DIR = Path(__file__).parent / "models"

NON_FEATURES = {
    "fixture_id", "league_name", "match_date",
    "home_team_name", "away_team_name",
    "goals_home", "goals_away",
    "result", "over05", "over15", "over25", "over35", "btts", "total_goals", "total_corners",
    "home_team_id", "away_team_id",
    "season",
}

CATEGORICAL_FEATURES = ["league_id"]

# Features de empate protegidas del feature selection: siempre se incluyen aunque
# su importancia global sea baja (son minority-class-specific y el selector las descarta).
DRAW_PINNED_FEATURES = [
    "home_season_draw_rate",
    "away_season_draw_rate",
    "team_draw_vs_league",
    "draw_tendency_index",
    "draw_tendency_diff",
    "h2h_draws",
    "league_draw_rate",
    "league_season_draw_rate",
    "match_balance_index",
    "ppg_balance",
    "xg_balance",
    "both_draw_prone",
    "draw_rate_diff_recent",
    "both_teams_recent_draw_rate",
]

# Features disponibles para early-season (alta cobertura incluso en partidos tempranos / ligas nuevas)
EARLY_SEASON_FEATURES = [
    "elo_diff", "elo_win_prob", "elo_draw_factor",
    "elo_prob_home", "elo_prob_draw", "elo_prob_away",
    "feature_reliability", "min_team_season_games", "early_season_flag",
    "home_season_ppg_cred", "away_season_ppg_cred", "diff_ppg_cred",
    "elo_diff_x_reliability", "elo_home_x_unreliability",
    "blended_home_prob", "blended_draw_prob", "blended_away_prob",
    "diff_prev_ppg_filled", "diff_prev_season_gd_pg",
    "elo_reliability", "min_total_elo_games",
    "home_total_elo_games", "away_total_elo_games",
    "diff_blended_ppg", "diff_blended_gd_pg",
    "league_home_win_rate", "league_draw_rate", "league_avg_goals",
    "league_season_draw_rate", "league_over25_rate", "league_btts_rate",
    "league_id", "league_tier",
    "home_days_rest", "away_days_rest",
    "diff_prev_season_ppg", "diff_prev_season_gfpg",
    "home_prev_season_gd_pg", "away_prev_season_gd_pg",
]

# Features Elo y fiabilidad: siempre incluidas en el modelo 1x2 (clave para early-season)
ELO_1X2_PINNED = [
    "elo_diff", "elo_win_prob", "elo_draw_factor",
    "elo_prob_home", "elo_prob_draw", "elo_prob_away",
    "feature_reliability", "min_team_season_games", "early_season_flag",
    "home_season_ppg_cred", "away_season_ppg_cred", "diff_ppg_cred",
    "elo_diff_x_reliability", "elo_home_x_unreliability",
    "blended_home_prob", "blended_draw_prob", "blended_away_prob",
    "diff_prev_ppg_filled", "diff_prev_season_gd_pg",
    "elo_reliability", "min_total_elo_games",
    "home_total_elo_games", "away_total_elo_games",
    "diff_blended_ppg", "diff_blended_gd_pg",
]

# Features críticas para el regresor Poisson de goles totales
GOALS_PINNED_FEATURES = [
    "combined_xg", "defensive_porosity", "total_season_goal_rate", "goal_threat_product",
    "league_avg_goals", "league_over25_rate", "league_over15_rate", "league_btts_rate",
    "home_roll_xg_for_last5", "away_roll_xg_for_last5",
    "home_roll_xg_against_last5", "away_roll_xg_against_last5",
    "home_roll_ou25_last5", "away_roll_ou25_last5",
    "home_roll_ou15_last5", "away_roll_ou15_last5",
    "home_roll_goals_for_last5", "away_roll_goals_for_last5",
    "home_roll_goals_against_last5", "away_roll_goals_against_last5",
    "roll_goals_for_h_last5", "roll_goals_against_h_last5",
    "home_season_gfpg", "away_season_gfpg", "home_season_gapg", "away_season_gapg",
    "diff_xg", "diff_xga",
    "diff_goals_for", "diff_goals_against",
]

# Features críticas para BTTS — protegidas en selección para modelos BTTS
BTTS_PINNED_FEATURES = [
    "both_teams_score_rate", "clean_sheet_clash",
    "home_roll_scored_last5", "away_roll_scored_last5",
    "home_roll_clean_sheet_last5", "away_roll_clean_sheet_last5",
    "home_roll_btts_last5", "away_roll_btts_last5",
    "league_btts_rate",
    "home_roll_xg_for_last5", "away_roll_xg_against_last5",
    "home_roll_xg_against_last5", "away_roll_xg_for_last5",
    "home_season_gfpg", "away_season_gfpg",
]

# Features críticas para córners — protegidas para el regresor Poisson
CORNERS_PINNED_FEATURES = [
    "combined_corners",
    "home_roll_corner_kicks_last5", "away_roll_corner_kicks_last5",
    "home_roll_corners_against_last5", "away_roll_corners_against_last5",
    "home_roll_corner_ratio_last5", "away_roll_corner_ratio_last5",
    "diff_corners", "diff_corners_against", "corner_dominance_diff",
    "home_roll_possession_last5", "away_roll_possession_last5",
    "diff_possession",
    "league_avg_corners",
]


# ---------------------------------------------------------------------------
# RPS — Ranked Probability Score
# ---------------------------------------------------------------------------

def rps_score(y_true: np.ndarray, y_prob: np.ndarray) -> float:
    n = len(y_true)
    rps_total = 0.0
    for i in range(n):
        oh = np.zeros(3)
        oh[int(y_true[i])] = 1.0
        cum_pred = np.cumsum(y_prob[i])
        cum_true = np.cumsum(oh)
        rps_total += np.sum((cum_pred[:-1] - cum_true[:-1]) ** 2) / 2
    return rps_total / n


def _softmax_from_probs(probs: np.ndarray, temperature: float) -> np.ndarray:
    probs = np.clip(probs, 1e-12, 1.0)
    logits = np.log(probs) / max(float(temperature), 1e-6)
    logits = logits - logits.max(axis=1, keepdims=True)
    exp = np.exp(logits)
    return exp / exp.sum(axis=1, keepdims=True)


def _sigmoid_from_probs(probs: np.ndarray, temperature: float) -> np.ndarray:
    probs = np.clip(probs, 1e-12, 1 - 1e-12)
    logits = np.log(probs / (1 - probs)) / max(float(temperature), 1e-6)
    return 1.0 / (1.0 + np.exp(-logits))


def _normalize_rows(probs: np.ndarray) -> np.ndarray:
    probs = np.clip(probs, 1e-12, 1.0)
    return probs / probs.sum(axis=1, keepdims=True)


class CalibratedLGBM:
    """Wrapper picklable: corrección de prior para class_weight + temperature scaling.
    task puede ser: 'multiclass', 'binary', 'poisson_regressor'.
    early_season_extra_temp: temperatura adicional para partidos con feature_reliability < 1
    (partidos early-season), linealmente interpolada por (1 - reliability)."""

    def __init__(self, model, task: str, temperature: float = 1.0,
                 class_weight: Optional[dict] = None, positive_weight: Optional[float] = None,
                 early_season_extra_temp: float = 0.0):
        self.model = model
        self.task = task
        self.temperature = float(temperature)
        self.early_season_extra_temp = float(early_season_extra_temp)
        self.class_weight = class_weight or {}
        self.positive_weight = positive_weight
        self.best_iteration_ = getattr(model, "best_iteration_", None)
        self.feature_importances_ = getattr(model, "feature_importances_", None)

    def _prior_correct_multiclass(self, probs: np.ndarray) -> np.ndarray:
        if not self.class_weight:
            return _normalize_rows(probs)
        weights = np.array([self.class_weight.get(i, 1.0) for i in range(probs.shape[1])], dtype=float)
        return _normalize_rows(probs / np.clip(weights, 1e-12, None))

    def _prior_correct_binary(self, p: np.ndarray) -> np.ndarray:
        if not self.positive_weight or self.positive_weight <= 0:
            return np.clip(p, 1e-12, 1 - 1e-12)
        odds = p / np.clip(1 - p, 1e-12, None)
        corrected_odds = odds / self.positive_weight
        return corrected_odds / (1 + corrected_odds)

    def predict_proba(self, X):
        raw = self.model.predict_proba(X)
        if self.task == "multiclass":
            corrected = self._prior_correct_multiclass(raw)
            extra = getattr(self, "early_season_extra_temp", 0.0)
            if extra > 0 and hasattr(X, "columns") and "feature_reliability" in X.columns:
                # Temperatura dinámica: más alta para early-season (feature_reliability < 1)
                reliability = np.clip(X["feature_reliability"].values, 0.0, 1.0)
                dynamic_temp = self.temperature + extra * (1.0 - reliability)
                corrected_c = np.clip(corrected, 1e-12, 1.0)
                logits = np.log(corrected_c) / np.maximum(dynamic_temp[:, None], 1e-6)
                logits -= logits.max(axis=1, keepdims=True)
                exp_v = np.exp(logits)
                return exp_v / exp_v.sum(axis=1, keepdims=True)
            return _softmax_from_probs(corrected, self.temperature)
        p1 = self._prior_correct_binary(raw[:, 1])
        p1 = _sigmoid_from_probs(p1, self.temperature)
        return np.column_stack([1 - p1, p1])

    def predict(self, X):
        probs = self.predict_proba(X)
        if self.task == "multiclass":
            return np.argmax(probs, axis=1)
        return (probs[:, 1] >= 0.5).astype(int)

    def predict_lambda(self, X) -> np.ndarray:
        """Para regresor Poisson: devuelve lambda (valor esperado). Solo task='poisson_regressor'."""
        if self.task != "poisson_regressor":
            raise ValueError("predict_lambda solo disponible para task='poisson_regressor'")
        return np.maximum(self.model.predict(X), 0.0)  # lambda >= 0


# ---------------------------------------------------------------------------
# Carga y preparación
# ---------------------------------------------------------------------------

def load_dataset(path: Path) -> pd.DataFrame:
    df = pd.read_parquet(path)
    df["match_date"] = pd.to_datetime(df["match_date"], utc=True)
    return df.sort_values("match_date").reset_index(drop=True)


def build_prev_season_ratings(df: pd.DataFrame) -> dict[int, float]:
    """Ratings Elo iniciales basados en el GD/partido de la temporada anterior.
    Mejora las predicciones early-season para equipos nuevos en el dataset."""
    ratings: dict[int, float] = {}
    for prefix, tcol in [("home", "home_team_id"), ("away", "away_team_id")]:
        gf_col = f"{prefix}_prev_season_gfpg"
        ga_col = f"{prefix}_prev_season_gapg"
        if gf_col not in df.columns or ga_col not in df.columns or tcol not in df.columns:
            continue
        sub = df[[tcol, gf_col, ga_col]].dropna(subset=[gf_col, ga_col])
        for row in sub.itertuples(index=False):
            tid   = int(getattr(row, tcol))
            gd_pg = getattr(row, gf_col) - getattr(row, ga_col)
            elo   = 1500.0 + gd_pg * 150.0
            ratings[tid] = float(np.clip(elo, 1100.0, 1900.0))
    if ratings:
        print(f"    Elo init prev_season: {len(ratings)} equipos  "
              f"(rango [{min(ratings.values()):.0f}, {max(ratings.values()):.0f}])")
    return ratings


def compute_elo_features(df: pd.DataFrame, k: float = 32.0, home_advantage: float = 100.0,
                         initial_ratings: Optional[dict[int, float]] = None) -> pd.DataFrame:
    """
    Calcula ratings Elo incrementales por equipo a partir de toda la historia de partidos.
    Sin leakage: rating almacenado para cada partido = estado ANTES de ese partido.
    - elo_draw_factor: exp(-|elo_diff|/200) — alto cuando equipos muy igualados.
    - initial_ratings: Elo inicial por team_id (de prev_season si está disponible).
    """
    # K variable: más alta para equipos nuevos (convergen rápido), normal para establecidos.
    # Umbral: K_WARMUP partidos en el dataset.
    K_HIGH    = k * 3.0   # e.g. 96 para equipos nuevos
    K_WARMUP  = 25        # partidos hasta que el Elo "se establece"

    df_sorted   = df.sort_values("match_date").reset_index(drop=True)
    ratings:    dict[int, float] = dict(initial_ratings) if initial_ratings else {}
    total_games: dict[int, int]  = {}  # partidos históricos por equipo en el dataset
    records = []

    for _, row in df_sorted.iterrows():
        home_id  = int(row["home_team_id"])
        away_id  = int(row["away_team_id"])
        home_r   = ratings.get(home_id, 1500.0)
        away_r   = ratings.get(away_id, 1500.0)
        elo_diff = home_r - away_r
        exp_home = 1.0 / (1.0 + 10.0 ** ((away_r - (home_r + home_advantage)) / 400.0))

        home_tg = total_games.get(home_id, 0)
        away_tg = total_games.get(away_id, 0)

        records.append({
            "fixture_id":          int(row["fixture_id"]),
            "home_elo":            home_r,
            "away_elo":            away_r,
            "elo_diff":            elo_diff,
            "elo_win_prob":        exp_home,
            "elo_draw_factor":     float(np.exp(-abs(elo_diff) / 200.0)),
            "home_total_elo_games": home_tg,
            "away_total_elo_games": away_tg,
        })

        goals_h = row.get("goals_home", np.nan)
        goals_a = row.get("goals_away", np.nan)
        if pd.notna(goals_h) and pd.notna(goals_a):
            actual = 1.0 if goals_h > goals_a else (0.5 if goals_h == goals_a else 0.0)
            k_h    = K_HIGH if home_tg < K_WARMUP else k
            k_a    = K_HIGH if away_tg < K_WARMUP else k
            k_eff  = (k_h + k_a) / 2.0  # zero-sum
            delta  = k_eff * (actual - exp_home)
            ratings[home_id]    = home_r + delta
            ratings[away_id]    = away_r - delta
            total_games[home_id] = home_tg + 1
            total_games[away_id] = away_tg + 1

    return pd.DataFrame(records)


def add_derived_features(df: pd.DataFrame) -> pd.DataFrame:
    """
    Agrega features derivadas de las columnas existentes del parquet.
    No requiere regenerar el parquet ni acceso a BD.
    Incluye: interacción ataque-vs-defensa, momentum (EMA vs rolling),
    tasa de conversión, tasa de paradas del portero y goles de temporada.
    """
    df = df.copy()
    n = 5

    gf_h  = f"home_roll_goals_for_last{n}"
    gf_a  = f"away_roll_goals_for_last{n}"
    ga_h  = f"home_roll_goals_against_last{n}"
    ga_a  = f"away_roll_goals_against_last{n}"

    # --- Interacción ataque vs defensa del rival ---
    if all(c in df.columns for c in [gf_h, ga_a]):
        df["home_attack_vs_away_def"] = df[gf_h] - df[ga_a]
    if all(c in df.columns for c in [gf_a, ga_h]):
        df["away_attack_vs_home_def"] = df[gf_a] - df[ga_h]
    if "home_attack_vs_away_def" in df.columns and "away_attack_vs_home_def" in df.columns:
        df["total_expected_goals_roll"] = df["home_attack_vs_away_def"] + df["away_attack_vs_home_def"]
        df["attack_def_imbalance"]      = df["home_attack_vs_away_def"] - df["away_attack_vs_home_def"]

    # --- Momentum de victorias: EMA - rolling (positivo = mejorando) ---
    ema_won_h  = f"home_ema_won_span{n}"
    ema_won_a  = f"away_ema_won_span{n}"
    roll_won_h = f"home_roll_won_last{n}"
    roll_won_a = f"away_roll_won_last{n}"
    if ema_won_h in df.columns and roll_won_h in df.columns:
        df["home_win_momentum"] = df[ema_won_h] - df[roll_won_h]
    if ema_won_a in df.columns and roll_won_a in df.columns:
        df["away_win_momentum"] = df[ema_won_a] - df[roll_won_a]
    if "home_win_momentum" in df.columns and "away_win_momentum" in df.columns:
        df["diff_win_momentum"] = df["home_win_momentum"] - df["away_win_momentum"]

    # --- Momentum ofensivo: EMA - rolling de goles marcados ---
    ema_gf_h = f"home_ema_goals_for_span{n}"
    ema_gf_a = f"away_ema_goals_for_span{n}"
    if ema_gf_h in df.columns and gf_h in df.columns:
        df["home_goals_momentum"] = df[ema_gf_h] - df[gf_h]
    if ema_gf_a in df.columns and gf_a in df.columns:
        df["away_goals_momentum"] = df[ema_gf_a] - df[gf_a]
    if "home_goals_momentum" in df.columns and "away_goals_momentum" in df.columns:
        df["diff_goals_momentum"] = df["home_goals_momentum"] - df["away_goals_momentum"]

    # --- Momentum defensivo: rolling - EMA de goles concedidos (positivo = mejorando) ---
    ema_ga_h = f"home_ema_goals_against_span{n}"
    ema_ga_a = f"away_ema_goals_against_span{n}"
    if ema_ga_h in df.columns and ga_h in df.columns:
        df["home_def_momentum"] = df[ga_h] - df[ema_ga_h]
    if ema_ga_a in df.columns and ga_a in df.columns:
        df["away_def_momentum"] = df[ga_a] - df[ema_ga_a]
    if "home_def_momentum" in df.columns and "away_def_momentum" in df.columns:
        df["diff_def_momentum"] = df["home_def_momentum"] - df["away_def_momentum"]

    # --- Tasa de conversión: goles / disparos al arco ---
    sog_h = f"home_roll_shots_on_goal_last{n}"
    sog_a = f"away_roll_shots_on_goal_last{n}"
    if sog_h in df.columns and gf_h in df.columns:
        df["home_conversion_rate"] = df[gf_h] / (df[sog_h] + 0.5)
    if sog_a in df.columns and gf_a in df.columns:
        df["away_conversion_rate"] = df[gf_a] / (df[sog_a] + 0.5)
    if "home_conversion_rate" in df.columns and "away_conversion_rate" in df.columns:
        df["diff_conversion_rate"] = df["home_conversion_rate"] - df["away_conversion_rate"]

    # --- Tasa de paradas: saves / (saves + goles concedidos) ---
    saves_h = f"home_roll_saves_last{n}"
    saves_a = f"away_roll_saves_last{n}"
    if saves_h in df.columns and ga_h in df.columns:
        shots_faced_h = df[saves_h] + df[ga_h]
        df["home_save_rate"] = df[saves_h] / (shots_faced_h + 0.5)
    if saves_a in df.columns and ga_a in df.columns:
        shots_faced_a = df[saves_a] + df[ga_a]
        df["away_save_rate"] = df[saves_a] / (shots_faced_a + 0.5)
    if "home_save_rate" in df.columns and "away_save_rate" in df.columns:
        df["diff_save_rate"] = df["home_save_rate"] - df["away_save_rate"]

    # --- Goal difference por partido (temporada) ---
    if "home_season_gfpg" in df.columns and "home_season_gapg" in df.columns:
        df["home_season_gd_pg"] = df["home_season_gfpg"] - df["home_season_gapg"]
    if "away_season_gfpg" in df.columns and "away_season_gapg" in df.columns:
        df["away_season_gd_pg"] = df["away_season_gfpg"] - df["away_season_gapg"]
    if "home_season_gd_pg" in df.columns and "away_season_gd_pg" in df.columns:
        df["diff_season_gd_pg"] = df["home_season_gd_pg"] - df["away_season_gd_pg"]

    # --- Features de credibilidad de estadísticas de temporada ---
    # Las estadísticas de temporada son ruidosas al inicio. Se pondera por games/15 (clipeado a 1).
    # Ayuda al modelo a confiar en Elo/rolling cuando hay pocos partidos jugados.
    for prefix in ["home", "away"]:
        games_col = f"{prefix}_season_games"
        if games_col in df.columns:
            cred = np.minimum(df[games_col] / 15.0, 1.0)
            for stat in ["season_ppg", "season_gfpg", "season_gapg", "season_draw_rate"]:
                col = f"{prefix}_{stat}"
                if col in df.columns:
                    df[f"{prefix}_{stat}_cred"] = df[col] * cred
    if "home_season_ppg_cred" in df.columns and "away_season_ppg_cred" in df.columns:
        df["diff_ppg_cred"] = df["home_season_ppg_cred"] - df["away_season_ppg_cred"]
    if "home_season_gfpg_cred" in df.columns and "away_season_gfpg_cred" in df.columns:
        df["diff_gfpg_cred"] = df["home_season_gfpg_cred"] - df["away_season_gfpg_cred"]
    if "home_season_gapg_cred" in df.columns and "away_season_gapg_cred" in df.columns:
        df["diff_gapg_cred"] = df["home_season_gapg_cred"] - df["away_season_gapg_cred"]

    # --- Forma reciente vs temporada: detecta equipos que divergen de su promedio ---
    if gf_h in df.columns and "home_season_gfpg" in df.columns:
        df["home_form_vs_season"] = df[gf_h] - df["home_season_gfpg"]
    if gf_a in df.columns and "away_season_gfpg" in df.columns:
        df["away_form_vs_season"] = df[gf_a] - df["away_season_gfpg"]
    if "home_form_vs_season" in df.columns and "away_form_vs_season" in df.columns:
        df["diff_form_vs_season"] = df["home_form_vs_season"] - df["away_form_vs_season"]

    # --- Fiabilidad de features de temporada (early-season awareness) ---
    # El modelo usa estas features para aprender cuándo confiar en stats de temporada vs Elo.
    if "home_season_games" in df.columns and "away_season_games" in df.columns:
        home_g = df["home_season_games"].fillna(0)
        away_g = df["away_season_games"].fillna(0)
        df["min_team_season_games"] = np.minimum(home_g, away_g)
        df["feature_reliability"]   = np.minimum(df["min_team_season_games"] / 10.0, 1.0)
        df["early_season_flag"]     = (df["min_team_season_games"] < 5).astype(float)

    # Fiabilidad del Elo (basada en partidos totales en el dataset)
    if "home_total_elo_games" in df.columns and "away_total_elo_games" in df.columns:
        df["min_total_elo_games"] = np.minimum(df["home_total_elo_games"], df["away_total_elo_games"])
        df["elo_reliability"]     = np.minimum(df["min_total_elo_games"] / 50.0, 1.0)

    # --- Probabilidades explícitas Elo (baseline robusto para early-season) ---
    # Estas features permiten al modelo usarlas directamente como prior cuando
    # los stats de temporada son ruidosos.
    if "elo_win_prob" in df.columns and "elo_draw_factor" in df.columns:
        _base_draw = 0.27
        _elo_draw  = df["elo_draw_factor"] * _base_draw
        _elo_home  = df["elo_win_prob"] * (1.0 - _elo_draw)
        _elo_away  = (1.0 - df["elo_win_prob"]) * (1.0 - _elo_draw)
        _total     = _elo_home + _elo_draw + _elo_away
        df["elo_prob_home"] = _elo_home / _total
        df["elo_prob_draw"] = _elo_draw / _total
        df["elo_prob_away"] = _elo_away / _total

    # Interacción Elo × fiabilidad: cuánto señal hay cuando las features temporales son ruido
    if "feature_reliability" in df.columns:
        if "elo_diff" in df.columns:
            df["elo_diff_x_reliability"]    = df["elo_diff"]    * df["feature_reliability"]
            df["elo_diff_x_unreliability"]  = df["elo_diff"]    * (1.0 - df["feature_reliability"])
        if "elo_prob_home" in df.columns:
            df["elo_home_x_reliability"]    = df["elo_prob_home"] * df["feature_reliability"]
            df["elo_home_x_unreliability"]  = df["elo_prob_home"] * (1.0 - df["feature_reliability"])

    # --- Probabilidad blended (Elo + liga) según fiabilidad de temporada ---
    # Para early-season: prediction ≈ league_rate × Elo-adjusted
    # Para late-season: la fiabilidad de las stats de temporada toma el control
    if "elo_prob_home" in df.columns and "league_home_win_rate" in df.columns:
        rel = df.get("feature_reliability", pd.Series(1.0, index=df.index)).fillna(1.0)
        lhr = df["league_home_win_rate"].fillna(0.44)
        ldr = df["league_draw_rate"].fillna(0.26)
        lar = 1.0 - lhr - ldr
        df["blended_home_prob"] = rel * df["elo_prob_home"] + (1.0 - rel) * lhr
        df["blended_draw_prob"] = rel * df["elo_prob_draw"].fillna(ldr) + (1.0 - rel) * ldr
        df["blended_away_prob"] = rel * df["elo_prob_away"] + (1.0 - rel) * lar

    # --- prev_season GD por partido (más informativo que GFPG/GAPG por separado) ---
    if "home_prev_season_gfpg" in df.columns and "home_prev_season_gapg" in df.columns:
        df["home_prev_season_gd_pg"] = df["home_prev_season_gfpg"] - df["home_prev_season_gapg"]
    if "away_prev_season_gfpg" in df.columns and "away_prev_season_gapg" in df.columns:
        df["away_prev_season_gd_pg"] = df["away_prev_season_gfpg"] - df["away_prev_season_gapg"]
    if "home_prev_season_gd_pg" in df.columns and "away_prev_season_gd_pg" in df.columns:
        df["diff_prev_season_gd_pg"] = df["home_prev_season_gd_pg"] - df["away_prev_season_gd_pg"]

    # --- prev_season PPG con fill de media de liga (elimina 44% de nulos) ---
    # Para equipos sin datos de temporada anterior, la mejor estimación es la media de la liga
    if "home_prev_season_ppg" in df.columns and "league_home_win_rate" in df.columns:
        _lppg_h = df["league_home_win_rate"].fillna(0.44) * 3.0 + df["league_draw_rate"].fillna(0.26)
        _lppg_a = (1 - df["league_home_win_rate"].fillna(0.44) - df["league_draw_rate"].fillna(0.26)) * 3.0 + df["league_draw_rate"].fillna(0.26)
        df["home_prev_ppg_filled"] = df["home_prev_season_ppg"].fillna(_lppg_h)
        df["away_prev_ppg_filled"] = df["away_prev_season_ppg"].fillna(_lppg_a)
        df["diff_prev_ppg_filled"] = df["home_prev_ppg_filled"] - df["away_prev_ppg_filled"]

    # --- Features de carrera (cross-season, sin reinicio) ---
    # Disponibles para early-season porque no dependen de stats de la temporada actual.
    if "home_career_gfpg" in df.columns and "home_career_gapg" in df.columns:
        df["home_career_gd_pg"] = df["home_career_gfpg"] - df["home_career_gapg"]
        df["away_career_gd_pg"] = df["away_career_gfpg"] - df["away_career_gapg"]
        df["diff_career_gd_pg"] = df["home_career_gd_pg"] - df["away_career_gd_pg"]
    if "home_career_ppg" in df.columns and "away_career_ppg" in df.columns:
        df["diff_career_ppg"] = df["home_career_ppg"] - df["away_career_ppg"]
    # Credibilidad de carrera: menos ruido para equipos con más partidos
    if "home_career_ppg" in df.columns and "home_career_games" in df.columns:
        h_cred = np.minimum(df["home_career_games"] / 20.0, 1.0)
        a_cred = np.minimum(df["away_career_games"] / 20.0, 1.0)
        df["home_career_ppg_cred"] = df["home_career_ppg"].fillna(1.5) * h_cred
        df["away_career_ppg_cred"] = df["away_career_ppg"].fillna(1.5) * a_cred

    # --- Blended season+prevseason (transición suave de early a late season) ---
    # Para early-season: usa prev_season como prior → siempre no-nulo
    # Para late-season: usa stats actuales de temporada (más relevantes)
    if "feature_reliability" in df.columns and "home_prev_ppg_filled" in df.columns:
        rel = df["feature_reliability"].fillna(0.0)
        if "home_season_ppg" in df.columns:
            h_szn = df["home_season_ppg"].fillna(df["home_prev_ppg_filled"])
            a_szn = df["away_season_ppg"].fillna(df["away_prev_ppg_filled"])
            df["home_blended_ppg"] = rel * h_szn + (1.0 - rel) * df["home_prev_ppg_filled"]
            df["away_blended_ppg"] = rel * a_szn + (1.0 - rel) * df["away_prev_ppg_filled"]
            df["diff_blended_ppg"] = df["home_blended_ppg"] - df["away_blended_ppg"]
        if "home_season_gd_pg" in df.columns and "home_prev_season_gd_pg" in df.columns:
            prev_gd_h = df["home_prev_season_gd_pg"].fillna(0.0)
            prev_gd_a = df["away_prev_season_gd_pg"].fillna(0.0)
            h_gd  = df["home_season_gd_pg"].fillna(prev_gd_h)
            a_gd  = df["away_season_gd_pg"].fillna(prev_gd_a)
            df["home_blended_gd_pg"] = rel * h_gd + (1.0 - rel) * prev_gd_h
            df["away_blended_gd_pg"] = rel * a_gd + (1.0 - rel) * prev_gd_a
            df["diff_blended_gd_pg"] = df["home_blended_gd_pg"] - df["away_blended_gd_pg"]

    # --- Strength of Schedule (SOS) ajustado: PPG ajustado por dificultad de rivales ---
    # SOS = Elo medio de rivales enfrentados → equipos con PPG alto contra rivales fuertes
    # son más peligrosos que equipos con mismo PPG contra rivales débiles.
    if "home_season_sos" in df.columns and "home_season_ppg" in df.columns:
        _league_elo = 1500.0
        h_sos = df["home_season_sos"].fillna(_league_elo)
        a_sos = df["away_season_sos"].fillna(_league_elo)
        df["home_adj_season_ppg"] = df["home_season_ppg"].fillna(0.0) * (h_sos / _league_elo)
        df["away_adj_season_ppg"] = df["away_season_ppg"].fillna(0.0) * (a_sos / _league_elo)
        df["diff_adj_season_ppg"] = df["home_adj_season_ppg"] - df["away_adj_season_ppg"]
        df["diff_season_sos"]     = h_sos - a_sos

    # --- Venue-specific season stats: local en casa, visitante fuera ---
    # home_vh_ppg: PPG del local solo en partidos en casa esta temporada (shift(1))
    # away_va_ppg: PPG del visitante solo en partidos fuera esta temporada
    # Captura ventaja/desventaja venue que home_season_ppg oculta al mezclar home+away.
    if "home_vh_ppg" in df.columns and "away_va_ppg" in df.columns:
        h_vg = df.get("home_vh_games", pd.Series(0, index=df.index)).fillna(0)
        a_vg = df.get("away_va_games", pd.Series(0, index=df.index)).fillna(0)
        h_vcred = np.minimum(h_vg / 8.0, 1.0)
        a_vcred = np.minimum(a_vg / 8.0, 1.0)
        # Fill con overall season PPG cuando no hay suficientes datos venue-specific
        h_fallback = df.get("home_season_ppg", pd.Series(1.5, index=df.index)).fillna(1.5)
        a_fallback = df.get("away_season_ppg", pd.Series(1.0, index=df.index)).fillna(1.0)
        h_vppg = df["home_vh_ppg"].fillna(h_fallback)
        a_vppg = df["away_va_ppg"].fillna(a_fallback)
        df["diff_venue_ppg"]      = h_vppg - a_vppg
        df["home_venue_ppg_cred"] = h_vppg * h_vcred
        df["away_venue_ppg_cred"] = a_vppg * a_vcred
        df["diff_venue_ppg_cred"] = df["home_venue_ppg_cred"] - df["away_venue_ppg_cred"]
        if "home_vh_gfpg" in df.columns:
            h_vgd = df["home_vh_gfpg"].fillna(0.0) - df["home_vh_gapg"].fillna(0.0)
            a_vgd = df["away_va_gfpg"].fillna(0.0) - df["away_va_gapg"].fillna(0.0)
            df["diff_venue_gd_pg"] = h_vgd - a_vgd

    return df


def compute_career_features(df: pd.DataFrame) -> pd.DataFrame:
    """Estadísticas de carrera acumuladas por equipo (toda la historia hasta cada partido).
    Sin reinicio de temporada → disponibles incluso para early-season.
    Sin leakage: solo usa partidos ANTERIORES al partido actual."""
    # Construir historia lineal (team_id, fixture_id, match_date, gf, ga)
    h_rows = df[["fixture_id", "home_team_id", "match_date", "goals_home", "goals_away"]].dropna(
        subset=["goals_home", "goals_away"]
    ).rename(columns={"home_team_id": "team_id", "goals_home": "gf", "goals_away": "ga"})
    a_rows = df[["fixture_id", "away_team_id", "match_date", "goals_home", "goals_away"]].dropna(
        subset=["goals_home", "goals_away"]
    ).rename(columns={"away_team_id": "team_id", "goals_away": "gf", "goals_home": "ga"})

    hist = pd.concat([h_rows, a_rows], ignore_index=True).sort_values(["team_id", "match_date"])
    hist["pts"] = (hist["gf"] > hist["ga"]) * 3 + (hist["gf"] == hist["ga"]) * 1

    # Shift(1) + expanding sum por equipo → acumulado ANTES de cada partido
    hist["_cp"]   = hist.groupby("team_id")["pts"].transform(lambda x: x.shift(1).expanding().sum())
    hist["_cgf"]  = hist.groupby("team_id")["gf"].transform(lambda x: x.shift(1).expanding().sum())
    hist["_cga"]  = hist.groupby("team_id")["ga"].transform(lambda x: x.shift(1).expanding().sum())
    hist["_cgames"] = hist.groupby("team_id")["pts"].transform(lambda x: x.shift(1).expanding().count())

    g = hist["_cgames"].replace(0, np.nan)
    hist["career_ppg"]   = hist["_cp"]  / g
    hist["career_gfpg"]  = hist["_cgf"] / g
    hist["career_gapg"]  = hist["_cga"] / g
    hist["career_games"] = hist["_cgames"].fillna(0).astype(int)

    home_sel = df[["fixture_id", "home_team_id"]].merge(
        hist[["fixture_id", "team_id", "career_ppg", "career_gfpg", "career_gapg", "career_games"]],
        left_on=["fixture_id", "home_team_id"], right_on=["fixture_id", "team_id"], how="left"
    ).rename(columns={"career_ppg": "home_career_ppg", "career_gfpg": "home_career_gfpg",
                      "career_gapg": "home_career_gapg", "career_games": "home_career_games"}
    ).drop(columns=["home_team_id", "team_id"], errors="ignore")[["fixture_id",
        "home_career_ppg", "home_career_gfpg", "home_career_gapg", "home_career_games"]]

    away_sel = df[["fixture_id", "away_team_id"]].merge(
        hist[["fixture_id", "team_id", "career_ppg", "career_gfpg", "career_gapg", "career_games"]],
        left_on=["fixture_id", "away_team_id"], right_on=["fixture_id", "team_id"], how="left"
    ).rename(columns={"career_ppg": "away_career_ppg", "career_gfpg": "away_career_gfpg",
                      "career_gapg": "away_career_gapg", "career_games": "away_career_games"}
    ).drop(columns=["away_team_id", "team_id"], errors="ignore")[["fixture_id",
        "away_career_ppg", "away_career_gfpg", "away_career_gapg", "away_career_games"]]

    result = home_sel.merge(away_sel, on="fixture_id", how="outer")
    return result


def compute_venue_season_stats(df: pd.DataFrame) -> pd.DataFrame:
    """Stats de temporada específicas por venue: local en casa, visitante fuera.
    home_team_home_ppg: PPG del local cuando juega EN CASA esta temporada (shift(1), sin leakage).
    away_team_away_ppg: PPG del visitante cuando juega FUERA esta temporada.
    Más preciso que home_season_ppg que mezcla home+away."""
    needed = {"fixture_id","home_team_id","away_team_id","season","match_date","goals_home","goals_away"}
    if not needed.issubset(df.columns):
        return df[["fixture_id"]].copy()

    def _venue_stats(rows: pd.DataFrame, prefix: str) -> pd.DataFrame:
        """Acumulado por (team_id, season) con shift(1). rows debe tener: team_id, gf, ga, season, match_date."""
        parts = []
        for (tid, seas), grp in rows.groupby(["team_id","season"], sort=False):
            grp = grp.sort_values("match_date").copy()
            s_pts = grp["pts"].shift(1)
            s_gf  = grp["gf"].shift(1)
            s_ga  = grp["ga"].shift(1)
            cum_g = s_pts.notna().cumsum()
            g_safe = cum_g.replace(0, np.nan)
            grp[f"{prefix}_ppg"]   = s_pts.cumsum() / g_safe
            grp[f"{prefix}_gfpg"]  = s_gf.cumsum()  / g_safe
            grp[f"{prefix}_gapg"]  = s_ga.cumsum()  / g_safe
            grp[f"{prefix}_games"] = cum_g.fillna(0).astype(int)
            parts.append(grp[["fixture_id","team_id",
                               f"{prefix}_ppg", f"{prefix}_gfpg",
                               f"{prefix}_gapg", f"{prefix}_games"]])
        return pd.concat(parts, ignore_index=True) if parts else pd.DataFrame()

    h = (df[["fixture_id","home_team_id","season","match_date","goals_home","goals_away"]]
         .dropna(subset=["goals_home","goals_away"])
         .rename(columns={"home_team_id":"team_id","goals_home":"gf","goals_away":"ga"}).copy())
    h["pts"] = (h["gf"] > h["ga"]) * 3 + (h["gf"] == h["ga"]) * 1

    a = (df[["fixture_id","away_team_id","season","match_date","goals_home","goals_away"]]
         .dropna(subset=["goals_home","goals_away"])
         .rename(columns={"away_team_id":"team_id","goals_away":"gf","goals_home":"ga"}).copy())
    a["pts"] = (a["gf"] > a["ga"]) * 3 + (a["gf"] == a["ga"]) * 1

    home_venue = _venue_stats(h, "vh")
    away_venue = _venue_stats(a, "va")

    res_h = (df[["fixture_id","home_team_id"]]
             .merge(home_venue, left_on=["fixture_id","home_team_id"],
                    right_on=["fixture_id","team_id"], how="left")
             .rename(columns={"vh_ppg":"home_vh_ppg","vh_gfpg":"home_vh_gfpg",
                               "vh_gapg":"home_vh_gapg","vh_games":"home_vh_games"})
             [["fixture_id","home_vh_ppg","home_vh_gfpg","home_vh_gapg","home_vh_games"]])

    res_a = (df[["fixture_id","away_team_id"]]
             .merge(away_venue, left_on=["fixture_id","away_team_id"],
                    right_on=["fixture_id","team_id"], how="left")
             .rename(columns={"va_ppg":"away_va_ppg","va_gfpg":"away_va_gfpg",
                               "va_gapg":"away_va_gapg","va_games":"away_va_games"})
             [["fixture_id","away_va_ppg","away_va_gfpg","away_va_gapg","away_va_games"]])

    return res_h.merge(res_a, on="fixture_id", how="outer")


def compute_sos_features(df: pd.DataFrame) -> pd.DataFrame:
    """Strength of Schedule: Elo medio de rivales enfrentados por (equipo, temporada).
    Sin leakage: solo cuenta partidos ANTERIORES al actual (shift(1)+cumsum).
    Requiere home_elo/away_elo en df (del merge con compute_elo_features)."""
    if "home_elo" not in df.columns or "away_elo" not in df.columns:
        return df[["fixture_id"]].copy()

    h_rows = df[["fixture_id", "home_team_id", "season", "match_date", "away_elo"]].rename(
        columns={"home_team_id": "team_id", "away_elo": "opp_elo"})
    a_rows = df[["fixture_id", "away_team_id", "season", "match_date", "home_elo"]].rename(
        columns={"away_team_id": "team_id", "home_elo": "opp_elo"})
    hist = pd.concat([h_rows, a_rows], ignore_index=True)

    parts = []
    for (team_id, season), grp in hist.groupby(["team_id", "season"], sort=False):
        grp = grp.sort_values("match_date").copy()
        shifted = grp["opp_elo"].shift(1)
        cum_sum   = shifted.cumsum()
        cum_count = shifted.notna().cumsum()
        grp["season_sos"] = cum_sum / cum_count.replace(0, np.nan)
        parts.append(grp[["fixture_id", "team_id", "season_sos"]])

    if not parts:
        return df[["fixture_id"]].copy()

    sos_all = pd.concat(parts, ignore_index=True)

    home_sos = (df[["fixture_id", "home_team_id"]]
                .merge(sos_all, left_on=["fixture_id", "home_team_id"],
                       right_on=["fixture_id", "team_id"], how="left")
                .rename(columns={"season_sos": "home_season_sos"})
                [["fixture_id", "home_season_sos"]])

    away_sos = (df[["fixture_id", "away_team_id"]]
                .merge(sos_all, left_on=["fixture_id", "away_team_id"],
                       right_on=["fixture_id", "team_id"], how="left")
                .rename(columns={"season_sos": "away_season_sos"})
                [["fixture_id", "away_season_sos"]])

    return home_sos.merge(away_sos, on="fixture_id", how="outer")


def _compute_match_weights(df: pd.DataFrame) -> Optional[np.ndarray]:
    """Pesos de entrenamiento: downpesa partidos early-season con features ruidosas.
    Reduce overfitting de patterns de late-season que no generalizan a 2025 early-season."""
    if "home_season_games" not in df.columns or "away_season_games" not in df.columns:
        return None
    home_cred = np.minimum(df["home_season_games"].fillna(0) / 10.0, 1.0)
    away_cred = np.minimum(df["away_season_games"].fillna(0) / 10.0, 1.0)
    weights   = home_cred * away_cred
    return (0.15 + 0.85 * weights).values


def get_feature_columns(df: pd.DataFrame, drop_league_id: bool = False) -> list[str]:
    features = [c for c in df.columns if c not in NON_FEATURES]
    if drop_league_id and "league_id" in features:
        features.remove("league_id")
    return features


def time_split(df: pd.DataFrame, test_seasons: list[int]) -> tuple[pd.DataFrame, pd.DataFrame]:
    if test_seasons:
        test  = df[df["season"].isin(test_seasons)]
        train = df[~df["season"].isin(test_seasons)]
    else:
        split_idx = int(len(df) * 0.80)
        train = df.iloc[:split_idx]
        test  = df.iloc[split_idx:]
    return train, test


def encode_categoricals(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()
    for col in CATEGORICAL_FEATURES:
        if col in df.columns:
            df[col] = df[col].astype("category")
    return df


def _cat_features_present(features: list[str]) -> list[str]:
    return [f for f in CATEGORICAL_FEATURES if f in features]


def prune_features(train: pd.DataFrame, features: list[str], max_null: float = 0.45) -> list[str]:
    kept = []
    for f in features:
        s = train[f]
        if s.isna().mean() > max_null:
            continue
        if f not in CATEGORICAL_FEATURES and s.nunique(dropna=True) <= 1:
            continue
        kept.append(f)
    return kept


def _select_top_n(train: pd.DataFrame, features: list[str], target: str,
                  max_n: int, task: str) -> list[str]:
    """Selecciona top-N features de una lista (sin pinned) usando un modelo selector."""
    if not features or max_n <= 0 or len(features) <= max_n:
        return features

    val_idx = int(len(train) * 0.85)
    tr  = train.iloc[:val_idx]
    val = train.iloc[val_idx:]
    cat_feats = _cat_features_present(features)

    if task == "multiclass":
        selector = lgb.LGBMClassifier(
            n_estimators=900, learning_rate=0.03, num_leaves=15, min_child_samples=90,
            subsample=0.75, subsample_freq=1, colsample_bytree=0.55,
            reg_alpha=1.5, reg_lambda=3.0, objective="multiclass", num_class=3,
            metric="multi_logloss", random_state=43, n_jobs=-1, verbose=-1,
        )
    else:
        selector = lgb.LGBMClassifier(
            n_estimators=900, learning_rate=0.025, num_leaves=12, min_child_samples=110,
            subsample=0.75, subsample_freq=1, colsample_bytree=0.55,
            reg_alpha=1.5, reg_lambda=3.0, objective="binary", metric="binary_logloss",
            random_state=43, n_jobs=-1, verbose=-1,
        )

    selector.fit(
        tr[features], tr[target],
        eval_set=[(val[features], val[target])],
        categorical_feature=cat_feats if cat_feats else "auto",
        callbacks=[lgb.early_stopping(80, verbose=False)],
    )
    imp = pd.Series(selector.feature_importances_, index=features).sort_values(ascending=False)
    selected = imp[imp > 0].head(max_n).index.tolist()
    return selected if selected else imp.head(max_n).index.tolist()


def select_top_features(train: pd.DataFrame, features: list[str], target: str,
                        max_features: int, task: str,
                        pinned_extra: list[str] | None = None) -> list[str]:
    """
    Selección de features con pinning por modelo.
    Si max_features=0 devuelve todas las features sin selección.
    Si max_features>0, protege DRAW_PINNED_FEATURES + pinned_extra y selecciona el resto.
    """
    if not max_features or len(features) <= max_features:
        return features

    all_pinned = list(dict.fromkeys(DRAW_PINNED_FEATURES + (pinned_extra or [])))
    pinned   = [f for f in all_pinned if f in features]
    general  = [f for f in features if f not in set(pinned)]
    n_general = max(max_features - len(pinned), 10)
    selected_general = _select_top_n(train, general, target, n_general, task)

    return pinned + selected_general


# ---------------------------------------------------------------------------
# Hiperparámetros (regularización fuerte de v2)
# ---------------------------------------------------------------------------

LGBM_BASE = {
    "n_estimators":      2500,
    "learning_rate":     0.015,
    "num_leaves":        18,
    "min_child_samples": 90,
    "subsample":         0.72,
    "subsample_freq":    1,
    "colsample_bytree":  0.48,
    "reg_alpha":         1.2,
    "reg_lambda":        2.8,
    "random_state":      42,
    "n_jobs":            -1,
    "verbose":           -1,
}

# Regularización extrema para 1x2: combate el overfitting estructural (gap=0.04+)
LGBM_1X2 = {
    **LGBM_BASE,
    "num_leaves":        7,
    "min_child_samples": 400,
    "colsample_bytree":  0.40,
    "reg_alpha":         5.0,
    "reg_lambda":        12.0,
    "subsample":         0.58,
}

# Regularización para modelo early-season: menos features → menos necesidad de regularizar
LGBM_1X2_EARLY = {
    **LGBM_BASE,
    "num_leaves":        7,
    "min_child_samples": 250,
    "colsample_bytree":  0.80,
    "reg_alpha":         3.0,
    "reg_lambda":        8.0,
    "subsample":         0.65,
}

LGBM_BINARY = {
    **LGBM_BASE,
    "num_leaves":        12,
    "min_child_samples": 160,
    "learning_rate":     0.012,
    "colsample_bytree":  0.45,
    "reg_alpha":         1.5,
    "reg_lambda":        4.5,
}

LGBM_REGRESSOR = {
    **LGBM_BASE,
    "num_leaves":        16,
    "min_child_samples": 100,
    "learning_rate":     0.012,
    "colsample_bytree":  0.50,
    "reg_alpha":         1.0,
    "reg_lambda":        2.0,
}

EARLY_STOPPING_ROUNDS = 180


def _fit_base_model(model: lgb.LGBMClassifier, train: pd.DataFrame, features: list[str], target: str,
                    sample_weight: Optional[np.ndarray] = None):
    val_idx   = int(len(train) * 0.85)
    tr        = train.iloc[:val_idx]
    val       = train.iloc[val_idx:]
    cat_feats = _cat_features_present(features)
    sw_tr     = sample_weight[:val_idx] if sample_weight is not None else None
    model.fit(
        tr[features], tr[target],
        sample_weight=sw_tr,
        eval_set=[(val[features], val[target])],
        categorical_feature=cat_feats if cat_feats else "auto",
        callbacks=[
            lgb.early_stopping(EARLY_STOPPING_ROUNDS, verbose=False),
            lgb.log_evaluation(period=200),
        ],
    )
    print(f"    Best iteration: {model.best_iteration_}")
    return model, val


def _find_best_temperature_multiclass(model, val: pd.DataFrame, features: list[str], class_weight: dict) -> float:
    y   = val["result"].values
    raw = model.predict_proba(val[features])
    weights   = np.array([class_weight.get(i, 1.0) for i in range(raw.shape[1])], dtype=float)
    corrected = _normalize_rows(raw / np.clip(weights, 1e-12, None))
    # temperatura >= 1.10: suavizado moderado para generalizar a temporadas nuevas
    candidates = np.round(np.arange(1.10, 2.31, 0.05), 2)
    scores = [(rps_score(y, _softmax_from_probs(corrected, t)), t) for t in candidates]
    return min(scores)[1]


def _find_best_temp_and_extra(model, val: pd.DataFrame, features: list[str], class_weight: dict) -> tuple[float, float]:
    """Búsqueda 2D de (temperature, early_season_extra_temp) sobre validación.
    extra_temp: temperatura adicional para early-season (feature_reliability < 1)."""
    y   = val["result"].values
    raw = model.predict_proba(val[features])
    weights   = np.array([class_weight.get(i, 1.0) for i in range(raw.shape[1])], dtype=float)
    corrected = _normalize_rows(raw / np.clip(weights, 1e-12, None))
    corrected_c = np.clip(corrected, 1e-12, 1.0)

    if "feature_reliability" in val.columns:
        reliability = np.clip(val["feature_reliability"].values, 0.0, 1.0)
    else:
        reliability = np.ones(len(val))

    best_rps  = float("inf")
    best_temp = 1.0
    best_extra = 0.0

    for temp in np.round(np.arange(1.00, 1.81, 0.10), 2):
        for extra in np.round(np.arange(0.0, 1.21, 0.20), 2):
            dyn = temp + extra * (1.0 - reliability)
            logits = np.log(corrected_c) / np.maximum(dyn[:, None], 1e-6)
            logits -= logits.max(axis=1, keepdims=True)
            exp_v   = np.exp(logits)
            probs   = exp_v / exp_v.sum(axis=1, keepdims=True)
            rps     = rps_score(y, probs)
            if rps < best_rps:
                best_rps   = rps
                best_temp  = temp
                best_extra = extra

    return best_temp, best_extra


def _find_best_temperature_binary(model, val: pd.DataFrame, features: list[str], target: str,
                                  positive_weight: Optional[float]) -> float:
    y = val[target].values
    p = model.predict_proba(val[features])[:, 1]
    if positive_weight and positive_weight > 0:
        odds = p / np.clip(1 - p, 1e-12, None)
        p = (odds / positive_weight) / (1 + odds / positive_weight)
    candidates = np.round(np.arange(0.90, 2.51, 0.05), 2)
    scores = [(log_loss(y, np.column_stack([1 - _sigmoid_from_probs(p, t), _sigmoid_from_probs(p, t)])), t)
              for t in candidates]
    return min(scores)[1]


class TwoModelPredictor:
    """Combina un modelo early-season y uno late-season.
    Enruta cada partido al modelo adecuado según min_team_season_games."""

    def __init__(self, early: CalibratedLGBM, late: CalibratedLGBM,
                 early_features: list[str], late_features: list[str],
                 threshold: int = 5):
        self.early = early
        self.late  = late
        self.early_features = early_features
        self.late_features  = late_features
        self.threshold      = threshold
        # Para compatibilidad con evaluate_1x2 (task='multiclass')
        self.task = "multiclass"
        self.best_iteration_  = None
        self.feature_importances_ = None

    def predict_proba(self, X: pd.DataFrame) -> np.ndarray:
        result = np.zeros((len(X), 3))
        if "min_team_season_games" not in X.columns:
            late_f = [f for f in self.late_features if f in X.columns]
            return self.late.predict_proba(X[late_f])
        early_mask = (X["min_team_season_games"].fillna(0) < self.threshold).values
        if early_mask.any():
            ef = [f for f in self.early_features if f in X.columns]
            result[early_mask] = self.early.predict_proba(X.loc[early_mask, ef])
        if (~early_mask).any():
            lf = [f for f in self.late_features if f in X.columns]
            result[~early_mask] = self.late.predict_proba(X.loc[~early_mask, lf])
        return result

    def predict(self, X: pd.DataFrame) -> np.ndarray:
        return np.argmax(self.predict_proba(X), axis=1)


def train_1x2_two_model(train: pd.DataFrame, all_features: list[str],
                        df_all: pd.DataFrame) -> tuple:
    """Entrena modelo early + modelo late por separado y devuelve
    (TwoModelPredictor, early_features, late_features)."""
    cw_train   = {0: 1.0, 1: 1.30, 2: 1.0}
    cw_correct = {0: 1.0, 1: 1.10, 2: 1.0}

    # --- Modelo early: solo partidos con < 5 juegos de temporada ---
    early_mask = (train.get("min_team_season_games", pd.Series(10, index=train.index)).fillna(0) < 5)
    train_early = train[early_mask]
    early_fs = [f for f in EARLY_SEASON_FEATURES if f in all_features and f in train_early.columns]
    early_fs = prune_features(train_early, early_fs, max_null=0.60)
    print(f"    [early] {len(train_early)} partidos  |  {len(early_fs)} features")

    model_early = lgb.LGBMClassifier(
        **LGBM_1X2_EARLY, objective="multiclass", num_class=3,
        class_weight=cw_train, metric="multi_logloss",
    )
    model_early, val_early = _fit_base_model(model_early, train_early, early_fs, "result")
    temp_early = _find_best_temperature_multiclass(model_early, val_early, early_fs, cw_correct)
    print(f"    [early] Calibration temperature: {temp_early:.2f}")
    cal_early = CalibratedLGBM(model_early, task="multiclass", temperature=temp_early,
                               class_weight=cw_correct)

    # --- Modelo late: solo partidos con >= 5 juegos de temporada ---
    late_mask = ~early_mask
    train_late = train[late_mask]
    late_fs = [f for f in all_features if f in train_late.columns]
    late_fs = prune_features(train_late, late_fs, max_null=0.45)
    print(f"    [late]  {len(train_late)} partidos  |  {len(late_fs)} features")

    model_late = lgb.LGBMClassifier(
        **LGBM_1X2, objective="multiclass", num_class=3,
        class_weight=cw_train, metric="multi_logloss",
    )
    sw_late = _compute_match_weights(train_late)
    if sw_late is not None:
        print(f"    [late]  Sample weights: media={sw_late.mean():.3f}")
    model_late, val_late = _fit_base_model(model_late, train_late, late_fs, "result",
                                           sample_weight=sw_late)
    temp_late = _find_best_temperature_multiclass(model_late, val_late, late_fs, cw_correct)
    print(f"    [late]  Calibration temperature: {temp_late:.2f}")
    cal_late = CalibratedLGBM(model_late, task="multiclass", temperature=temp_late,
                              class_weight=cw_correct)

    combined = TwoModelPredictor(cal_early, cal_late, early_fs, late_fs, threshold=5)
    return combined, early_fs, late_fs


def train_1x2(train: pd.DataFrame, features: list[str]) -> CalibratedLGBM:
    # cw_train: el modelo aprende a darle más peso al empate en la función de pérdida
    # cw_correct: corrección de prior más suave → evita subestimar empates post-corrección
    cw_train   = {0: 1.0, 1: 1.30, 2: 1.0}
    cw_correct = {0: 1.0, 1: 1.10, 2: 1.0}
    model = lgb.LGBMClassifier(
        **LGBM_1X2,
        objective="multiclass",
        num_class=3,
        class_weight=cw_train,
        metric="multi_logloss",
    )
    sw = _compute_match_weights(train)
    if sw is not None:
        print(f"    Sample weights: media={sw.mean():.3f}  min={sw.min():.3f}  max={sw.max():.3f}")
    model, val = _fit_base_model(model, train, features, "result", sample_weight=sw)
    temp = _find_best_temperature_multiclass(model, val, features, cw_correct)
    print(f"    Calibration temperature: {temp:.2f}")
    return CalibratedLGBM(model, task="multiclass", temperature=temp, class_weight=cw_correct)


def train_poisson_regressor(train: pd.DataFrame, features: list[str], target: str) -> CalibratedLGBM:
    """Regresor LightGBM con objetivo Poisson para predecir el número esperado de córners."""
    train_clean = train.dropna(subset=[target])
    if len(train_clean) < 50:
        raise ValueError(f"Solo {len(train_clean)} filas con {target} no-nulo. Mínimo 50.")
    val_idx   = int(len(train_clean) * 0.85)
    tr        = train_clean.iloc[:val_idx]
    val       = train_clean.iloc[val_idx:]
    cat_feats = _cat_features_present(features)

    model = lgb.LGBMRegressor(
        **LGBM_REGRESSOR,
        objective="poisson",
        metric="poisson",
    )
    model.fit(
        tr[features], tr[target],
        eval_set=[(val[features], val[target])],
        categorical_feature=cat_feats if cat_feats else "auto",
        callbacks=[
            lgb.early_stopping(EARLY_STOPPING_ROUNDS, verbose=False),
            lgb.log_evaluation(period=200),
        ],
    )
    print(f"    Best iteration: {model.best_iteration_}")
    return CalibratedLGBM(model, task="poisson_regressor")


def train_binary(train: pd.DataFrame, features: list[str], target: str) -> CalibratedLGBM:
    y   = train[target].values
    pos = max(float(np.sum(y == 1)), 1.0)
    neg = max(float(np.sum(y == 0)), 1.0)
    spw = float(np.sqrt(neg / pos))
    model = lgb.LGBMClassifier(
        **LGBM_BINARY,
        objective="binary",
        metric="binary_logloss",
        scale_pos_weight=spw,
    )
    model, val = _fit_base_model(model, train, features, target)
    temp = _find_best_temperature_binary(model, val, features, target, spw)
    print(f"    Calibration temperature: {temp:.2f}")
    return CalibratedLGBM(model, task="binary", temperature=temp, positive_weight=spw)


# ---------------------------------------------------------------------------
# Evaluación
# ---------------------------------------------------------------------------

def evaluate_1x2(model: CalibratedLGBM, data: pd.DataFrame, features: list[str], split_label: str = "Test") -> float:
    X = data[features]
    y = data["result"].values
    probs = model.predict_proba(X)
    preds = np.argmax(probs, axis=1)

    acc      = accuracy_score(y, preds)
    ll       = log_loss(y, probs)
    rps      = rps_score(y, probs)
    base_rps = rps_score(y, np.tile([1/3, 1/3, 1/3], (len(y), 1)))

    print(f"\n  Modelo 1X2 [{split_label} — {len(data)} partidos]")
    print(f"    Accuracy:          {acc:.3f}  (baseline aleatorio ≈ 0.333)")
    print(f"    Log Loss:          {ll:.4f}")
    print(f"    RPS:               {rps:.4f}  (baseline = {base_rps:.4f} | mejora: {(base_rps - rps) / base_rps:.1%})")

    print(f"\n    Calibración y Brier por clase:")
    print(f"    {'':12}  {'Freq.real':>10}  {'Prob.media':>10}  {'Brier':>7}  {'Brier base':>10}")
    briers = [brier_score_loss((y == i).astype(float), probs[:, i]) for i in range(3)]
    worst  = int(np.argmax(briers))
    for i, lbl in enumerate(["Local win", "Empate   ", "Away win "]):
        y_bin    = (y == i).astype(float)
        freq_r   = y_bin.mean()
        prob_m   = probs[:, i].mean()
        brier    = briers[i]
        b_base   = freq_r * (1 - freq_r)
        marker   = "  ← peor calibrado" if i == worst else ""
        print(f"      {lbl}   {freq_r:>9.1%}  {prob_m:>10.1%}  {brier:>7.4f}  {b_base:>10.4f}{marker}")

    if split_label == "Test":
        _print_confusion(y, preds)
    return rps


def evaluate_regression(model: CalibratedLGBM, data: pd.DataFrame, features: list[str],
                        target: str, label: str, split_label: str = "Test") -> float:
    data_clean = data.dropna(subset=[target])
    if data_clean.empty:
        print(f"\n  Modelo {label} [{split_label}] — sin datos con {target} no-nulo")
        return float("nan")
    X    = data_clean[features]
    y    = data_clean[target].values
    pred = model.predict_lambda(X)
    mae  = float(np.mean(np.abs(pred - y)))
    rmse = float(np.sqrt(np.mean((pred - y) ** 2)))
    bias = float(np.mean(pred - y))
    print(f"\n  Modelo {label} [{split_label} — {len(data_clean)} partidos]")
    print(f"    MAE:       {mae:.3f}  córners")
    print(f"    RMSE:      {rmse:.3f}  córners")
    print(f"    Bias:      {bias:+.3f}  (+ = sobreestima)")
    print(f"    λ media:   {pred.mean():.2f}  |  real media: {y.mean():.2f}")
    return mae


def evaluate_goals_poisson(model: CalibratedLGBM, data: pd.DataFrame, features: list[str],
                           split_label: str = "Test") -> float:
    """Evalúa el regresor Poisson de goles: MAE/RMSE + AUC derivado por umbral."""
    data_clean = data.dropna(subset=["total_goals"])
    if data_clean.empty:
        print(f"\n  Modelo Goals λ [{split_label}] — sin datos con total_goals no-nulo")
        return float("nan")
    X    = data_clean[features]
    y    = data_clean["total_goals"].values
    lam  = model.predict_lambda(X)
    mae  = float(np.mean(np.abs(lam - y)))
    rmse = float(np.sqrt(np.mean((lam - y) ** 2)))
    bias = float(np.mean(lam - y))
    print(f"\n  Modelo Goals λ [{split_label} — {len(data_clean)} partidos]")
    print(f"    MAE:       {mae:.3f}  goles")
    print(f"    RMSE:      {rmse:.3f}  goles")
    print(f"    Bias:      {bias:+.3f}  (+ = sobreestima)")
    print(f"    λ media:   {lam.mean():.2f}  |  real media: {y.mean():.2f}")
    print(f"\n    Derivados Poisson (monotonía garantizada):")
    print(f"    {'Mercado':<10}  {'AUC':>7}  {'Brier':>7}  {'Freq.real':>10}  {'Prob.media':>10}")
    for k, label in [(0, "Over 0.5"), (1, "Over 1.5"), (2, "Over 2.5"), (3, "Over 3.5")]:
        p_over = 1.0 - scipy_poisson.cdf(k, lam)
        y_bin  = (y > k + 0.5).astype(int)
        auc    = roc_auc_score(y_bin, p_over) if 0 < y_bin.sum() < len(y_bin) else float("nan")
        brier  = brier_score_loss(y_bin, p_over)
        print(f"    {label:<10}  {auc:>7.4f}  {brier:>7.4f}  {y_bin.mean():>9.1%}  {p_over.mean():>10.1%}")
    return mae


def evaluate_binary(model: CalibratedLGBM, data: pd.DataFrame, features: list[str],
                    target: str, label: str, split_label: str = "Test") -> float:
    X = data[features]
    y = data[target].values
    probs = model.predict_proba(X)[:, 1]
    preds = (probs >= 0.5).astype(int)

    acc      = accuracy_score(y, preds)
    auc      = roc_auc_score(y, probs)
    brier    = brier_score_loss(y, probs)
    ll       = log_loss(y, probs)
    freq_r   = y.mean()
    prob_m   = probs.mean()

    print(f"\n  Modelo {label} [{split_label} — {len(data)} partidos]")
    print(f"    Accuracy:          {acc:.3f}")
    print(f"    AUC-ROC:           {auc:.4f}  (aleatorio = 0.500)")
    print(f"    Brier Score:       {brier:.4f}  (base = {freq_r*(1-freq_r):.4f})")
    print(f"    Log Loss:          {ll:.4f}")
    print(f"    Calibración:       freq.real={freq_r:.1%}  prob.media={prob_m:.1%}")
    return auc


def _print_confusion(y_true, y_pred):
    labels = ["Local", "Empate", "Visit."]
    print(f"\n    Matriz de confusión (filas=real, cols=pred):")
    print(f"    {'':>8} " + "  ".join(f"{l:>7}" for l in labels))
    for i, lbl in enumerate(labels):
        row   = [sum((y_true == i) & (y_pred == j)) for j in range(3)]
        total = sum(y_true == i)
        print(f"    {lbl:>8} " + "  ".join(f"{v:>7}" for v in row) + f"   ({total} total)")

    draws_real    = sum(y_true == 1)
    draws_pred    = sum(y_pred == 1)
    draws_correct = sum((y_true == 1) & (y_pred == 1))
    if draws_real > 0:
        draw_recall    = draws_correct / draws_real
        draw_pred_rate = draws_pred / len(y_true)
        draw_real_rate = draws_real / len(y_true)
        print(f"\n    📊 ANÁLISIS DE EMPATES (clase 1):")
        print(f"       Empates reales:     {draws_real}")
        print(f"       Empates predichos:  {draws_pred}")
        print(f"       Empates acertados:  {draws_correct}")
        print(f"       Recall (recall=correct/real): {draw_recall:.1%}")
        if draw_pred_rate < draw_real_rate * 0.55:
            print(f"       ⚠️  CRÍTICO: Modelo subestima empates. Considera incrementar class_weight.")
        elif draw_pred_rate > draw_real_rate * 1.45:
            print(f"       ⚠️  CRÍTICO: Modelo sobreestima empates. Considera reducir class_weight o subir threshold.")


def print_top_features(model: CalibratedLGBM, features: list[str], n: int = 15) -> None:
    imp_vals = getattr(model, "feature_importances_", None)
    if imp_vals is None:
        imp_vals = getattr(model.model, "feature_importances_", np.zeros(len(features)))
    imp = pd.Series(imp_vals, index=features)
    top = imp.nlargest(n)
    print(f"\n  Top {n} features por importancia:")
    max_val = max(float(top.max()), 1.0)
    for feat, val in top.items():
        bar = "█" * int(val / max_val * 20)
        print(f"    {feat:<52} {bar}")


# ---------------------------------------------------------------------------
# Persistencia
# ---------------------------------------------------------------------------

def save_model(model: CalibratedLGBM, name: str, metadata: dict) -> None:
    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    path = MODELS_DIR / f"{name}.pkl"
    with open(path, "wb") as f:
        pickle.dump({"model": model, "metadata": metadata}, f)
    print(f"  → Guardado: {path}")


# ---------------------------------------------------------------------------
# Entrypoint
# ---------------------------------------------------------------------------

def parse_args():
    p = argparse.ArgumentParser()
    p.add_argument("--input", type=Path, default=INPUT_FILE)
    p.add_argument("--test-seasons", type=int, nargs="+", default=[])
    p.add_argument("--skip-btts", action="store_true")
    p.add_argument("--max-features", type=int, default=0,
                   help="Máximo de features tras selección. 0 = desactivado (todas las features). "
                        "Si se activa, DRAW_PINNED_FEATURES quedan protegidas.")
    p.add_argument("--drop-league-id", action="store_true")
    p.add_argument("--max-null", type=float, default=0.45)
    return p.parse_args()


def _prepare_features(df: pd.DataFrame, train: pd.DataFrame, base_features: list[str],
                      args, target: str, task: str,
                      pinned_extra: list[str] | None = None) -> list[str]:
    pruned   = prune_features(train, base_features, max_null=args.max_null)
    selected = select_top_features(train, pruned, target, args.max_features, task, pinned_extra=pinned_extra)
    return selected


def main():
    args = parse_args()

    print(f"Cargando dataset: {args.input}")
    if not args.input.exists():
        print(f"ERROR: no se encuentra {args.input}. Ejecuta feature_engineering_v3.py primero.")
        sys.exit(1)

    df = load_dataset(args.input)
    print("Calculando ratings Elo...")
    elo_df = compute_elo_features(df)
    df = df.merge(elo_df, on="fixture_id", how="left")
    print("Calculando Strength of Schedule (SOS)...")
    sos_df = compute_sos_features(df)
    df = df.merge(sos_df, on="fixture_id", how="left")
    print("Calculando venue-specific season stats (home en casa, away fuera)...")
    venue_df = compute_venue_season_stats(df)
    df = df.merge(venue_df, on="fixture_id", how="left")
    df = encode_categoricals(df)
    df = add_derived_features(df)
    base_features = get_feature_columns(df, drop_league_id=args.drop_league_id)
    print(f"  → {len(df)} partidos  |  {len(base_features)} features")

    train, test = time_split(df, args.test_seasons)
    print(f"  → Train: {len(train)} partidos  |  Test: {len(test)} partidos")
    if not args.test_seasons:
        print(f"  → Test desde: {test['match_date'].min().strftime('%Y-%m-%d')}")

    meta_base = {
        "train_size":      len(train),
        "test_size":       len(test),
        "train_start":     str(train["match_date"].min()),
        "train_end":       str(train["match_date"].max()),
        "test_start":      str(test["match_date"].min()),
        "test_end":        str(test["match_date"].max()),
        "max_features":    args.max_features,
        "drop_league_id":  args.drop_league_id,
        "max_null":        args.max_null,
        "config_name":     "v4_multi_market",
    }

    print(f"\n{'='*55}")

    # ── 1/4: 1X2 ──────────────────────────────────────────────────────────────
    print("\n[1/4] Entrenando modelo 1X2...")
    features_1x2 = _prepare_features(df, train, base_features, args, "result", "multiclass",
                                      pinned_extra=ELO_1X2_PINNED)
    if len(features_1x2) != len(base_features):
        print(f"    Feature selection: {len(base_features)} → {len(features_1x2)} features")
    m1x2    = train_1x2(train, features_1x2)
    rps_tr  = evaluate_1x2(m1x2, train, features_1x2, split_label="Train")
    rps_te  = evaluate_1x2(m1x2, test,  features_1x2, split_label="Test")
    overfit = rps_tr - rps_te
    print(f"\n    Overfitting check (RPS):  Train={rps_tr:.4f}  Test={rps_te:.4f}  gap={overfit:+.4f}"
          + ("  ⚠ posible overfitting" if overfit < -0.015 else "  ✓ OK"))
    print_top_features(m1x2, features_1x2)

    # Diagnóstico early/late season para entender dónde viene el gap de RPS
    if "min_team_season_games" in test.columns:
        early_mask = test["min_team_season_games"] < 5
        late_mask  = ~early_mask
        print(f"\n  Diagnóstico early/late season (test):")
        print(f"    Early (games<5): {early_mask.sum()} partidos ({early_mask.mean():.1%})")
        print(f"    Late  (games≥5): {late_mask.sum()} partidos ({late_mask.mean():.1%})")
        for label, mask in [("Early", early_mask), ("Late", late_mask)]:
            if mask.sum() > 0:
                sub = test.loc[mask]
                sub_probs = m1x2.predict_proba(sub[features_1x2])
                sub_rps  = rps_score(sub["result"].values, sub_probs)
                base_sub = rps_score(sub["result"].values, np.tile([1/3,1/3,1/3], (mask.sum(),1)))
                print(f"    RPS {label:5s}: {sub_rps:.4f}  (baseline={base_sub:.4f}  mejora={(base_sub-sub_rps)/base_sub:.1%})")

    save_model(m1x2, "lgbm_1x2", {**meta_base, "features": features_1x2, "target": "result", "classes": [0, 1, 2]})

    # ── 2/4: BTTS ──────────────────────────────────────────────────────────────
    if not args.skip_btts:
        print("\n[2/4] Entrenando modelo BTTS...")
        features_btts = _prepare_features(df, train, base_features, args, "btts", "binary", pinned_extra=BTTS_PINNED_FEATURES)
        if len(features_btts) != len(base_features):
            print(f"    Feature selection: {len(base_features)} → {len(features_btts)} features")
        mbtts      = train_binary(train, features_btts, "btts")
        auc_bt_tr  = evaluate_binary(mbtts, train, features_btts, "btts", "BTTS", split_label="Train")
        auc_bt_te  = evaluate_binary(mbtts, test,  features_btts, "btts", "BTTS", split_label="Test")
        print(f"\n    Overfitting check (AUC):  Train={auc_bt_tr:.4f}  Test={auc_bt_te:.4f}  gap={auc_bt_tr - auc_bt_te:+.4f}"
              + ("  ⚠ posible overfitting" if auc_bt_tr - auc_bt_te > 0.04 else "  ✓ OK"))
        print_top_features(mbtts, features_btts, n=10)
        save_model(mbtts, "lgbm_btts", {**meta_base, "features": features_btts, "target": "btts"})

    # ── 3/4: Goles totales (Poisson regressor) ────────────────────────────────
    # Sustituye los 3 clasificadores binarios (ou25, over15, over35).
    # λ → P(>k) via scipy_poisson.cdf garantiza monotonía entre umbrales.
    if "total_goals" in df.columns and df["total_goals"].notna().sum() >= 50:
        print("\n[3/4] Entrenando regresor Poisson de goles...")
        features_gl = _prepare_features(df, train, base_features, args, "total_goals", "binary",
                                        pinned_extra=GOALS_PINNED_FEATURES)
        try:
            mgl        = train_poisson_regressor(train, features_gl, "total_goals")
            mae_gl_tr  = evaluate_goals_poisson(mgl, train, features_gl, split_label="Train")
            mae_gl_te  = evaluate_goals_poisson(mgl, test,  features_gl, split_label="Test")
            print(f"\n    Overfitting check (MAE):  Train={mae_gl_tr:.3f}  Test={mae_gl_te:.3f}  gap={mae_gl_tr - mae_gl_te:+.3f}"
                  + ("  ⚠" if abs(mae_gl_tr - mae_gl_te) > 0.3 else "  ✓ OK"))
            print_top_features(mgl, features_gl, n=10)
            save_model(mgl, "lgbm_goals_lambda", {**meta_base, "features": features_gl, "target": "total_goals", "model_type": "poisson_regressor"})
        except ValueError as e:
            print(f"    ⚠ {e} — omitiendo regresor de goles.")
    else:
        print("\n[3/4] total_goals — columna no encontrada o insuficientes datos, omitiendo regresor de goles.")

    # ── 4/4: Córners (Poisson regressor) ──────────────────────────────────────
    if "total_corners" in df.columns and df["total_corners"].notna().sum() >= 50:
        print("\n[4/4] Entrenando regresor Poisson de córners...")
        features_cr = _prepare_features(df, train, base_features, args, "total_corners", "binary",
                                        pinned_extra=CORNERS_PINNED_FEATURES)
        try:
            mcr       = train_poisson_regressor(train, features_cr, "total_corners")
            mae_cr_tr = evaluate_regression(mcr, train, features_cr, "total_corners", "Corners λ", split_label="Train")
            mae_cr_te = evaluate_regression(mcr, test,  features_cr, "total_corners", "Corners λ", split_label="Test")
            print(f"\n    Overfitting check (MAE):  Train={mae_cr_tr:.3f}  Test={mae_cr_te:.3f}  gap={mae_cr_tr - mae_cr_te:+.3f}"
                  + ("  ⚠" if abs(mae_cr_tr - mae_cr_te) > 0.5 else "  ✓ OK"))
            print_top_features(mcr, features_cr, n=10)
            save_model(mcr, "lgbm_corners_lambda", {**meta_base, "features": features_cr, "target": "total_corners", "model_type": "poisson_regressor"})
        except ValueError as e:
            print(f"    ⚠ {e} — omitiendo modelo de córners.")
    else:
        print("\n[4/4] total_corners — columna no encontrada o insuficientes datos, omitiendo regresor de córners.")

    print(f"\n{'='*55}")
    print("Completado. Modelos guardados:")
    for name in ["lgbm_1x2", "lgbm_btts", "lgbm_goals_lambda", "lgbm_corners_lambda"]:
        path = MODELS_DIR / f"{name}.pkl"
        status = "✓" if path.exists() else "✗ no generado"
        print(f"  {status}  {path.name}")
    print("\nSiguiente paso → threshold_optimization.py (apunta a models/lgbm_1x2.pkl)")


if __name__ == "__main__":
    main()
