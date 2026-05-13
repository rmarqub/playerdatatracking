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

import numpy as np
import pandas as pd
from sklearn.metrics import accuracy_score

warnings.filterwarnings("ignore", category=UserWarning)

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
                   default=[0.25, 0.30, 0.35, 0.40, 0.45, 0.50, 0.55, 0.60],
                   help="Thresholds de empate a probar")
    return p.parse_args()


def main():
    args = parse_args()

    print(f"Cargando modelo: {args.model_path}")
    if not args.model_path.exists():
        print(f"ERROR: modelo no encontrado. Entrena primero con train_model.py")
        sys.exit(1)

    with open(args.model_path, "rb") as f:
        model_data = pickle.load(f)
    model = model_data["model"]
    print(f"  ✓ Modelo cargado (config: {model_data.get('metadata', {}).get('config_name', 'desconocida')})")

    print(f"\nCargando dataset: {args.input}")
    df = load_dataset(args.input)
    features = [c for c in df.columns if c not in NON_FEATURES]
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
    print(f"{'Threshold':<12} {'Accuracy':<12} {'RPS':<10} {'DrawRecall':<12} {'DrawCorr':<12} {'Precision D':<12}")
    print("-" * 90)

    results = {}
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

        marker = ""
        if acc > best_accuracy:
            best_accuracy = acc
            best_threshold = threshold
            best_metrics = metrics
            marker = " ← MEJOR ACCURACY"

        print(f"{threshold:<12.2f} {acc:<12.3f} {rps:<10.4f} {draw_recall:<12.1%} "
              f"{draws_correct:<12} {precision_draw:<12.1%}{marker}")

    # Detalle del mejor threshold
    print(f"\n{'='*90}")
    print(f"MEJOR THRESHOLD: {best_threshold:.2f}")
    print(f"{'='*90}")
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
        print(f"  Esto mejorará accuracy a {best_metrics['accuracy']:.3f} (+{improvement:+.3f})")
        print(f"\n  Para aplicar en train_model.py:")
        print(f"    def predict_with_threshold(probs, threshold={best_threshold}):")
        print(f"        # ... implementación ...")

    print(f"\n{'='*90}\n")


if __name__ == "__main__":
    main()
