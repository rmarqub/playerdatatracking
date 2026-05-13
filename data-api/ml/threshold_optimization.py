"""
Fase 3d: Optimización de threshold de predicción para mejorar Accuracy.

El modelo predice probabilidades (0, 1, 2) para cada clase.
Por defecto usa argmax, pero podemos ajustar el threshold de empate:
  - Si prob[1] > threshold_draw → predice empate (1)
  - Si no, compara prob[0] vs prob[2] → local (0) o visitante (2)

Esto permite mejorar accuracy sin reentrenar.

Uso:
    python threshold_optimization.py --best-config 0
"""

import argparse
import pickle
import sys
import warnings
from pathlib import Path
from typing import Optional

import numpy as np
import pandas as pd
from sklearn.metrics import accuracy_score, log_loss

warnings.filterwarnings("ignore", category=UserWarning)


# ---------------------------------------------------------------------------
# CalibratedLGBM — necesario para deserializar modelos guardados por train_model_v2/v3
# ---------------------------------------------------------------------------

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

INPUT_FILE = Path(__file__).parent / "training_data.parquet"
MODELS_DIR = Path(__file__).parent / "models"

NON_FEATURES = {
    "fixture_id", "league_name", "match_date",
    "home_team_name", "away_team_name",
    "goals_home", "goals_away",
    "result", "over25", "over15", "over35", "btts", "total_goals",
    "home_team_id", "away_team_id",
}

CATEGORICAL_FEATURES = ["league_id", "season"]


def load_dataset(path: Path) -> pd.DataFrame:
    df = pd.read_parquet(path)
    df["match_date"] = pd.to_datetime(df["match_date"], utc=True)
    return df.sort_values("match_date").reset_index(drop=True)


def get_feature_columns(df: pd.DataFrame) -> list[str]:
    return [c for c in df.columns if c not in NON_FEATURES]


def time_split(df: pd.DataFrame, test_seasons: list[int]):
    if test_seasons:
        test = df[df["season"].isin(test_seasons)]
        train = df[~df["season"].isin(test_seasons)]
    else:
        split_idx = int(len(df) * 0.80)
        train = df.iloc[:split_idx]
        test = df.iloc[split_idx:]
    return train, test


def rps_score(y_true: np.ndarray, y_prob: np.ndarray) -> float:
    """Ranked Probability Score."""
    n = len(y_true)
    rps_total = 0.0
    for i in range(n):
        oh = np.zeros(3)
        oh[int(y_true[i])] = 1.0
        cum_pred = np.cumsum(y_prob[i])
        cum_true = np.cumsum(oh)
        rps_total += np.sum((cum_pred[:-1] - cum_true[:-1]) ** 2) / 2
    return rps_total / n


def predict_with_threshold(probs: np.ndarray, threshold_draw: float = 0.5) -> np.ndarray:
    """
    Predicción customizable con threshold de empate.

    Si prob[i, 1] (empate) > threshold_draw:
        predice 1 (empate)
    Si no, predice argmax(prob[i, [0, 2]]) (local 0 o visitante 2)
    """
    preds = np.zeros(len(probs), dtype=int)
    for i in range(len(probs)):
        if probs[i, 1] > threshold_draw:
            preds[i] = 1
        else:
            # Elige entre local (0) y visitante (2)
            preds[i] = 0 if probs[i, 0] > probs[i, 2] else 2
    return preds


def compute_metrics(y_true: np.ndarray, y_pred: np.ndarray, probs: np.ndarray) -> dict:
    """Computa métricas para una predicción."""
    acc = accuracy_score(y_true, y_pred)
    rps = rps_score(y_true, probs)

    # Draw recall
    draws_real = sum(y_true == 1)
    draws_pred = sum(y_pred == 1)
    draws_correct = sum((y_true == 1) & (y_pred == 1))
    draw_recall = draws_correct / draws_real if draws_real > 0 else 0.0

    # Precision, recall por clase
    metrics_by_class = {}
    for class_id in [0, 1, 2]:
        class_name = ["Local", "Draw", "Away"][class_id]
        tp = sum((y_true == class_id) & (y_pred == class_id))
        fp = sum((y_true != class_id) & (y_pred == class_id))
        fn = sum((y_true == class_id) & (y_pred != class_id))

        precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
        recall = tp / (tp + fn) if (tp + fn) > 0 else 0.0
        f1 = 2 * (precision * recall) / (precision + recall) if (precision + recall) > 0 else 0.0

        metrics_by_class[class_name] = {
            "precision": precision,
            "recall": recall,
            "f1": f1,
        }

    return {
        "accuracy": acc,
        "rps": rps,
        "draw_recall": draw_recall,
        "draws_correct": draws_correct,
        "draws_total": draws_real,
        "metrics_by_class": metrics_by_class,
    }


def parse_args():
    p = argparse.ArgumentParser(description="Optimizar threshold de predicción")
    p.add_argument("--input", type=Path, default=INPUT_FILE)
    p.add_argument("--model-path", type=Path, default=MODELS_DIR / "lgbm_1x2.pkl",
                   help="Ruta del modelo guardado")
    p.add_argument("--test-seasons", type=int, nargs="+", default=[2025])
    p.add_argument("--thresholds", type=float, nargs="+",
                   default=[0.18, 0.22, 0.25, 0.28, 0.30, 0.32, 0.35, 0.38, 0.40, 0.45, 0.50, 0.55, 0.60],
                   help="Thresholds de empate a probar")
    p.add_argument("--objective", choices=["accuracy", "balanced", "draw_f1"], default="balanced",
                   help="Criterio para elegir threshold. balanced penaliza destruir el empate.")
    return p.parse_args()



def threshold_score(metrics: dict, objective: str) -> float:
    """Score interno para elegir threshold sin mirar solo accuracy."""
    if objective == "accuracy":
        return metrics["accuracy"]
    draw_f1 = metrics["metrics_by_class"]["Draw"]["f1"]
    if objective == "draw_f1":
        return draw_f1
    # Balance: accuracy principal, pero evita soluciones que ignoran o sobrepredicen masivamente el empate.
    local_f1 = metrics["metrics_by_class"]["Local"]["f1"]
    away_f1 = metrics["metrics_by_class"]["Away"]["f1"]
    macro_f1 = (local_f1 + draw_f1 + away_f1) / 3
    return 0.65 * metrics["accuracy"] + 0.35 * macro_f1

def main():
    args = parse_args()

    print(f"Cargando modelo: {args.model_path}")
    if not args.model_path.exists():
        print(f"ERROR: modelo no encontrado. Entrena primero con train_model.py")
        sys.exit(1)

    with open(args.model_path, "rb") as f:
        model_data = pickle.load(f)
    model = model_data["model"]
    metadata = model_data.get("metadata", {})
    print(f"  ✓ Modelo cargado (config: {metadata.get('config_name', 'desconocida')})")

    print(f"\nCargando dataset: {args.input}")
    df = load_dataset(args.input)
    features = metadata.get("features") or [c for c in df.columns if c not in NON_FEATURES]
    missing = [c for c in features if c not in df.columns]
    if missing:
        print(f"ERROR: el dataset no contiene {len(missing)} features usadas por el modelo. Ejemplo: {missing[:5]}")
        sys.exit(1)
    print(f"  → {len(df)} partidos  |  {len(features)} features")

    train, test = time_split(df, args.test_seasons)
    print(f"  → Test: {len(test)} partidos (temporada {args.test_seasons})")

    # Codificar categóricas
    for col in CATEGORICAL_FEATURES:
        if col in test.columns:
            test[col] = test[col].astype("category")

    # Obtener probabilidades en test
    print(f"\nGenerando predicciones en test...")
    X_test = test[features]
    y_test = test["result"].values
    probs_test = model.predict_proba(X_test)
    print(f"  ✓ {len(test)} predicciones de probabilidad")

    # Probar diferentes thresholds
    print(f"\n{'='*90}")
    print(f"ANÁLISIS DE THRESHOLD PARA EMPATE")
    print(f"{'='*90}")
    print(f"{'Threshold':<12} {'Accuracy':<12} {'RPS':<10} {'DrawRecall':<12} {'DrawCorr':<12} {'Precision D':<12} {'Score':<10}")
    print("-" * 100)

    results = {}
    best_score = -1.0
    best_accuracy = 0.0
    best_threshold = 0.5
    best_metrics = None

    for threshold in args.thresholds:
        preds = predict_with_threshold(probs_test, threshold)
        metrics = compute_metrics(y_test, preds, probs_test)
        results[threshold] = metrics

        acc = metrics["accuracy"]
        rps = metrics["rps"]
        draw_recall = metrics["draw_recall"]
        draws_correct = metrics["draws_correct"]
        precision_draw = metrics["metrics_by_class"]["Draw"]["precision"]

        score = threshold_score(metrics, args.objective)
        marker = ""
        if score > best_score:
            best_score = score
            best_accuracy = acc
            best_threshold = threshold
            best_metrics = metrics
            marker = " ← MEJOR SCORE"

        print(f"{threshold:<12.2f} {acc:<12.3f} {rps:<10.4f} {draw_recall:<12.1%} "
              f"{draws_correct:<12} {precision_draw:<12.1%} {score:<10.4f}{marker}")

    # Detalle del mejor threshold
    print(f"\n{'='*90}")
    print(f"MEJOR THRESHOLD: {best_threshold:.2f}")
    print(f"{'='*90}")
    print(f"  Objetivo usado:        {args.objective}")
    print(f"  Score interno:         {best_score:.4f}")
    print(f"  Accuracy:              {best_metrics['accuracy']:.3f}")
    print(f"  RPS:                   {best_metrics['rps']:.4f}")
    print(f"  Draw Recall:           {best_metrics['draw_recall']:.1%}")
    print(f"  Draws Predichos/Real:  {best_metrics['draws_correct']} / {best_metrics['draws_total']}")

    print(f"\n  Métricas por clase:")
    for class_name in ["Local", "Draw", "Away"]:
        m = best_metrics["metrics_by_class"][class_name]
        print(f"    {class_name:6} - Precision: {m['precision']:.1%}  Recall: {m['recall']:.1%}  F1: {m['f1']:.3f}")

    # Comparación con baseline (threshold 0.5)
    if 0.5 in results:
        baseline_acc = results[0.5]["accuracy"]
        improvement = best_metrics["accuracy"] - baseline_acc
        print(f"\n  Mejora vs baseline (threshold 0.5): {improvement:+.3f} ({improvement/baseline_acc:.1%})")

    # Recomendación
    print(f"\n{'='*90}")
    print(f"RECOMENDACIÓN")
    print(f"{'='*90}")
    if best_threshold == 0.5:
        print(f"✓ El threshold por defecto (0.5) es óptimo.")
    else:
        print(f"⚠ Cambiar threshold de empate a {best_threshold:.2f}")
        print(f"  Esto deja accuracy en {best_metrics['accuracy']:.3f} ({improvement:+.3f} vs threshold 0.5)")
        print(f"\n  Para aplicar en train_model.py:")
        print(f"    def predict_with_threshold(probs, threshold={best_threshold}):")
        print(f"        # ... implementación ...")

    print(f"\n{'='*90}\n")


if __name__ == "__main__":
    main()
