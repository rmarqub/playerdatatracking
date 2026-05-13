"""
Threshold optimization para Ensemble Stacking.
"""

import argparse
import pickle
from pathlib import Path
from typing import Tuple

import numpy as np
import pandas as pd
from sklearn.metrics import accuracy_score

INPUT_FILE = Path(__file__).parent / "training_data.parquet"
MODELS_DIR = Path(__file__).parent / "models"


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


def predict_with_threshold(probs: np.ndarray, threshold_draw: float = 0.5) -> np.ndarray:
    preds = np.zeros(len(probs), dtype=int)
    for i in range(len(probs)):
        if probs[i, 1] > threshold_draw:
            preds[i] = 1
        else:
            preds[i] = 0 if probs[i, 0] > probs[i, 2] else 2
    return preds


def compute_metrics(y_true: np.ndarray, y_pred: np.ndarray, probs: np.ndarray) -> dict:
    acc = accuracy_score(y_true, y_pred)
    rps = rps_score(y_true, probs)

    draws_real = sum(y_true == 1)
    draws_pred = sum(y_pred == 1)
    draws_correct = sum((y_true == 1) & (y_pred == 1))
    draw_recall = draws_correct / draws_real if draws_real > 0 else 0.0

    return {
        "accuracy": acc,
        "rps": rps,
        "draw_recall": draw_recall,
        "draws_correct": draws_correct,
        "draws_total": draws_real,
    }


def parse_args():
    p = argparse.ArgumentParser(description="Threshold optimization para ensemble")
    p.add_argument("--ensemble-path", type=Path, default=MODELS_DIR / "lgbm_1x2_stacking.pkl")
    p.add_argument("--thresholds", type=float, nargs="+",
                   default=[0.25, 0.30, 0.35, 0.40, 0.45, 0.50, 0.55, 0.60])
    return p.parse_args()


def main():
    args = parse_args()

    print(f"Cargando ensemble: {args.ensemble_path}")
    with open(args.ensemble_path, "rb") as f:
        ensemble_data = pickle.load(f)

    base_models = ensemble_data["base_models"]
    meta_model = ensemble_data["meta_model"]
    print(f"  ✓ Ensemble cargado (3 base models + meta-model)")

    # Cargar probabilidades pre-generadas
    probs_path = MODELS_DIR / "lgbm_1x2_stacking_probs.pkl"
    print(f"\nCargando probabilidades: {probs_path}")
    with open(probs_path, "rb") as f:
        probs_data = pickle.load(f)
    probs_test = probs_data["probs"]
    y_test = probs_data["y_true"]
    print(f"  ✓ {len(y_test)} predicciones")

    # Probar thresholds
    print(f"\n{'='*90}")
    print(f"ANÁLISIS DE THRESHOLD PARA ENSEMBLE")
    print(f"{'='*90}")
    print(f"{'Threshold':<12} {'Accuracy':<12} {'RPS':<10} {'DrawRecall':<12} {'DrawCorr':<12}")
    print("-" * 90)

    results = {}
    best_accuracy = 0.0
    best_threshold = 0.5

    for threshold in args.thresholds:
        preds = predict_with_threshold(probs_test, threshold)
        metrics = compute_metrics(y_test, preds, probs_test)
        results[threshold] = metrics

        acc = metrics["accuracy"]
        rps = metrics["rps"]
        draw_recall = metrics["draw_recall"]
        draws_correct = metrics["draws_correct"]

        marker = ""
        if acc > best_accuracy:
            best_accuracy = acc
            best_threshold = threshold
            marker = " ← MEJOR ACCURACY"

        print(f"{threshold:<12.2f} {acc:<12.3f} {rps:<10.4f} {draw_recall:<12.1%} "
              f"{draws_correct:<12}{marker}")

    # Detalle del mejor threshold
    print(f"\n{'='*90}")
    print(f"MEJOR THRESHOLD: {best_threshold:.2f}")
    print(f"{'='*90}")
    best_metrics = results[best_threshold]
    print(f"  Accuracy:              {best_metrics['accuracy']:.3f}")
    print(f"  RPS:                   {best_metrics['rps']:.4f}")
    print(f"  Draw Recall:           {best_metrics['draw_recall']:.1%}")
    print(f"  Draws Correct:         {best_metrics['draws_correct']} / {best_metrics['draws_total']}")

    print(f"\n{'='*90}\n")


if __name__ == "__main__":
    main()
