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

# Features críticas para Over/Under — protegidas en selección para modelos OU
OU_PINNED_FEATURES = [
    "combined_xg", "defensive_porosity", "total_season_goal_rate", "goal_threat_product",
    "league_avg_goals", "league_over25_rate", "league_over15_rate", "league_btts_rate",
    "home_roll_xg_for_last5", "away_roll_xg_for_last5",
    "home_roll_xg_against_last5", "away_roll_xg_against_last5",
    "home_roll_ou25_last5", "away_roll_ou25_last5",
    "home_roll_ou15_last5", "away_roll_ou15_last5",
    "home_season_gfpg", "away_season_gfpg", "home_season_gapg", "away_season_gapg",
    "diff_xg", "diff_xga",
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
    task puede ser: 'multiclass', 'binary', 'poisson_regressor'."""

    def __init__(self, model, task: str, temperature: float = 1.0,
                 class_weight: Optional[dict] = None, positive_weight: Optional[float] = None):
        self.model = model
        self.task = task
        self.temperature = float(temperature)
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
    "min_child_samples": 85,
    "subsample":         0.72,
    "subsample_freq":    1,
    "colsample_bytree":  0.48,
    "reg_alpha":         1.2,
    "reg_lambda":        2.5,
    "random_state":      42,
    "n_jobs":            -1,
    "verbose":           -1,
}

LGBM_BINARY = {
    **LGBM_BASE,
    "num_leaves":        12,
    "min_child_samples": 120,
    "learning_rate":     0.012,
    "colsample_bytree":  0.45,
    "reg_alpha":         1.5,
    "reg_lambda":        3.0,
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


def _fit_base_model(model: lgb.LGBMClassifier, train: pd.DataFrame, features: list[str], target: str):
    val_idx = int(len(train) * 0.85)
    tr  = train.iloc[:val_idx]
    val = train.iloc[val_idx:]
    cat_feats = _cat_features_present(features)
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
    return model, val


def _find_best_temperature_multiclass(model, val: pd.DataFrame, features: list[str], class_weight: dict) -> float:
    y   = val["result"].values
    raw = model.predict_proba(val[features])
    weights   = np.array([class_weight.get(i, 1.0) for i in range(raw.shape[1])], dtype=float)
    corrected = _normalize_rows(raw / np.clip(weights, 1e-12, None))
    candidates = np.round(np.arange(0.85, 2.31, 0.05), 2)
    scores = [(rps_score(y, _softmax_from_probs(corrected, t)), t) for t in candidates]
    return min(scores)[1]


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


def train_1x2(train: pd.DataFrame, features: list[str]) -> CalibratedLGBM:
    # v3: peso 1.22 para empate — sube discriminación sin sobredisparar probabilidades
    cw = {0: 1.0, 1: 1.22, 2: 1.0}
    model = lgb.LGBMClassifier(
        **LGBM_BASE,
        objective="multiclass",
        num_class=3,
        class_weight=cw,
        metric="multi_logloss",
    )
    model, val = _fit_base_model(model, train, features, "result")
    temp = _find_best_temperature_multiclass(model, val, features, cw)
    print(f"    Calibration temperature: {temp:.2f}")
    return CalibratedLGBM(model, task="multiclass", temperature=temp, class_weight=cw)


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
    df = encode_categoricals(df)
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

    # ── 1/7: 1X2 ──────────────────────────────────────────────────────────────
    print("\n[1/7] Entrenando modelo 1X2...")
    features_1x2 = _prepare_features(df, train, base_features, args, "result", "multiclass")
    if len(features_1x2) != len(base_features):
        print(f"    Feature selection: {len(base_features)} → {len(features_1x2)} features")
    m1x2    = train_1x2(train, features_1x2)
    rps_tr  = evaluate_1x2(m1x2, train, features_1x2, split_label="Train")
    rps_te  = evaluate_1x2(m1x2, test,  features_1x2, split_label="Test")
    overfit = rps_tr - rps_te
    print(f"\n    Overfitting check (RPS):  Train={rps_tr:.4f}  Test={rps_te:.4f}  gap={overfit:+.4f}"
          + ("  ⚠ posible overfitting" if overfit < -0.015 else "  ✓ OK"))
    print_top_features(m1x2, features_1x2)
    save_model(m1x2, "lgbm_1x2", {**meta_base, "features": features_1x2, "target": "result", "classes": [0, 1, 2]})

    # ── 2/7: Over/Under 2.5 ────────────────────────────────────────────────────
    print("\n[2/7] Entrenando modelo Over/Under 2.5...")
    features_ou = _prepare_features(df, train, base_features, args, "over25", "binary", pinned_extra=OU_PINNED_FEATURES)
    if len(features_ou) != len(base_features):
        print(f"    Feature selection: {len(base_features)} → {len(features_ou)} features")
    mou        = train_binary(train, features_ou, "over25")
    auc_ou_tr  = evaluate_binary(mou, train, features_ou, "over25", "Over/Under 2.5", split_label="Train")
    auc_ou_te  = evaluate_binary(mou, test,  features_ou, "over25", "Over/Under 2.5", split_label="Test")
    print(f"\n    Overfitting check (AUC):  Train={auc_ou_tr:.4f}  Test={auc_ou_te:.4f}  gap={auc_ou_tr - auc_ou_te:+.4f}"
          + ("  ⚠ posible overfitting" if auc_ou_tr - auc_ou_te > 0.04 else "  ✓ OK"))
    print_top_features(mou, features_ou, n=10)
    save_model(mou, "lgbm_ou25", {**meta_base, "features": features_ou, "target": "over25"})

    # ── 3/7: BTTS ──────────────────────────────────────────────────────────────
    if not args.skip_btts:
        print("\n[3/7] Entrenando modelo BTTS...")
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

    # ── 4/7: Over 0.5 ─────────────────────────────────────────────────────────
    if "over05" in df.columns:
        print("\n[4/7] Entrenando modelo Over 0.5...")
        features_ou05 = _prepare_features(df, train, base_features, args, "over05", "binary", pinned_extra=OU_PINNED_FEATURES)
        mou05         = train_binary(train, features_ou05, "over05")
        auc_05_tr     = evaluate_binary(mou05, train, features_ou05, "over05", "Over 0.5", split_label="Train")
        auc_05_te     = evaluate_binary(mou05, test,  features_ou05, "over05", "Over 0.5", split_label="Test")
        print(f"\n    Overfitting check (AUC):  Train={auc_05_tr:.4f}  Test={auc_05_te:.4f}  gap={auc_05_tr - auc_05_te:+.4f}"
              + ("  ⚠" if auc_05_tr - auc_05_te > 0.04 else "  ✓ OK"))
        save_model(mou05, "lgbm_over05", {**meta_base, "features": features_ou05, "target": "over05"})
    else:
        print("\n[4/7] Over 0.5 — target no encontrado en dataset, omitiendo.")

    # ── 5/7: Over 1.5 ─────────────────────────────────────────────────────────
    if "over15" in df.columns:
        print("\n[5/7] Entrenando modelo Over 1.5...")
        features_ou15 = _prepare_features(df, train, base_features, args, "over15", "binary", pinned_extra=OU_PINNED_FEATURES)
        mou15         = train_binary(train, features_ou15, "over15")
        auc_15_tr     = evaluate_binary(mou15, train, features_ou15, "over15", "Over 1.5", split_label="Train")
        auc_15_te     = evaluate_binary(mou15, test,  features_ou15, "over15", "Over 1.5", split_label="Test")
        print(f"\n    Overfitting check (AUC):  Train={auc_15_tr:.4f}  Test={auc_15_te:.4f}  gap={auc_15_tr - auc_15_te:+.4f}"
              + ("  ⚠" if auc_15_tr - auc_15_te > 0.04 else "  ✓ OK"))
        print_top_features(mou15, features_ou15, n=8)
        save_model(mou15, "lgbm_over15", {**meta_base, "features": features_ou15, "target": "over15"})
    else:
        print("\n[5/7] Over 1.5 — target no encontrado en dataset, omitiendo.")

    # ── 6/7: Over 3.5 ─────────────────────────────────────────────────────────
    if "over35" in df.columns:
        print("\n[6/7] Entrenando modelo Over 3.5...")
        features_ou35 = _prepare_features(df, train, base_features, args, "over35", "binary", pinned_extra=OU_PINNED_FEATURES)
        mou35         = train_binary(train, features_ou35, "over35")
        auc_35_tr     = evaluate_binary(mou35, train, features_ou35, "over35", "Over 3.5", split_label="Train")
        auc_35_te     = evaluate_binary(mou35, test,  features_ou35, "over35", "Over 3.5", split_label="Test")
        print(f"\n    Overfitting check (AUC):  Train={auc_35_tr:.4f}  Test={auc_35_te:.4f}  gap={auc_35_tr - auc_35_te:+.4f}"
              + ("  ⚠" if auc_35_tr - auc_35_te > 0.04 else "  ✓ OK"))
        print_top_features(mou35, features_ou35, n=8)
        save_model(mou35, "lgbm_over35", {**meta_base, "features": features_ou35, "target": "over35"})
    else:
        print("\n[6/7] Over 3.5 — target no encontrado en dataset, omitiendo.")

    # ── 7/7: Córners (Poisson regressor) ──────────────────────────────────────
    if "total_corners" in df.columns and df["total_corners"].notna().sum() >= 50:
        print("\n[7/7] Entrenando regresor Poisson de córners...")
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
        print("\n[7/7] total_corners — columna no encontrada o insuficientes datos, omitiendo regresor de córners.")

    print(f"\n{'='*55}")
    print("Completado. Modelos guardados:")
    for name in ["lgbm_1x2", "lgbm_ou25", "lgbm_btts", "lgbm_over05", "lgbm_over15", "lgbm_over35", "lgbm_corners_lambda"]:
        path = MODELS_DIR / f"{name}.pkl"
        status = "✓" if path.exists() else "✗ no generado"
        print(f"  {status}  {path.name}")
    print("\nSiguiente paso → threshold_optimization.py (apunta a models/lgbm_1x2.pkl)")


if __name__ == "__main__":
    main()
