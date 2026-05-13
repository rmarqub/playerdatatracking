"""
Fase 3: Entrenamiento de modelos LightGBM para predicción de partidos.

Entrena tres modelos independientes:
  - lgbm_1x2    : resultado 1X2  (multiclase: 0=local, 1=empate, 2=visitante)
  - lgbm_ou25   : Over/Under 2.5 goles (binario)
  - lgbm_btts   : Both Teams To Score (binario)

Validación: time-split temporal — nunca shuffle aleatorio en series temporales.
Métrica principal para 1X2: RPS (Ranked Probability Score), estándar en predicción de fútbol.

Uso:
    python train_model.py --input mi_dataset.parquet --test-seasons 2025
"""

import argparse
import pickle
import sys
import warnings
from pathlib import Path

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
    "result", "over25", "over15", "over35", "btts", "total_goals",
    # IDs de equipo excluidos: su fuerza ya queda capturada por season_ppg/gfpg/gapg
    # y las features de rolling form. Incluirlos hace que el modelo los use como crutch.
    "home_team_id", "away_team_id",
}

CATEGORICAL_FEATURES = ["league_id", "season"]


# ---------------------------------------------------------------------------
# RPS — Ranked Probability Score (métrica estándar en predicción de fútbol)
# ---------------------------------------------------------------------------

def rps_score(y_true: np.ndarray, y_prob: np.ndarray) -> float:
    """
    Penaliza predicciones alejadas del resultado real en sentido ordinal.
    Rango: 0 (perfecto) — 1 (pésimo).
    Aleatorio ≈ 0.25-0.30. Modelo competitivo: < 0.20.
    """
    n = len(y_true)
    rps_total = 0.0
    for i in range(n):
        oh = np.zeros(3)
        oh[int(y_true[i])] = 1.0
        cum_pred = np.cumsum(y_prob[i])
        cum_true = np.cumsum(oh)
        rps_total += np.sum((cum_pred[:-1] - cum_true[:-1]) ** 2) / 2
    return rps_total / n


# ---------------------------------------------------------------------------
# Carga y preparación
# ---------------------------------------------------------------------------

def load_dataset(path: Path) -> pd.DataFrame:
    df = pd.read_parquet(path)
    df["match_date"] = pd.to_datetime(df["match_date"], utc=True)
    return df.sort_values("match_date").reset_index(drop=True)


def get_feature_columns(df: pd.DataFrame) -> list[str]:
    return [c for c in df.columns if c not in NON_FEATURES]


def time_split(df: pd.DataFrame, test_seasons: list[int]) -> tuple[pd.DataFrame, pd.DataFrame]:
    if test_seasons:
        test  = df[df["season"].isin(test_seasons)]
        train = df[~df["season"].isin(test_seasons)]
    else:
        split_idx = int(len(df) * 0.80)
        train = df.iloc[:split_idx]
        test  = df.iloc[split_idx:]
    return train, test


def encode_categoricals(df: pd.DataFrame, features: list[str]) -> pd.DataFrame:
    """Convierte las columnas categóricas a tipo category para que LightGBM las trate correctamente."""
    df = df.copy()
    for col in CATEGORICAL_FEATURES:
        if col in df.columns:
            df[col] = df[col].astype("category")
    return df


# ---------------------------------------------------------------------------
# Hiperparámetros
# ---------------------------------------------------------------------------

LGBM_BASE = {
    "n_estimators":      3000,
    "learning_rate":     0.02,    # más lento → early stopping para en iteraciones más informativas
    "num_leaves":        35,      # ↓ reducido de 50 para evitar overfitting extremo en 1X2
    "min_child_samples": 30,      # ↑ aumentado de 20 para hojas menos específicas (reduce sesgo H/A)
    "subsample":         0.8,
    "subsample_freq":    1,
    "colsample_bytree":  0.60,    # ↓ reducido de 0.70 para menos features por árbol (más conservador)
    "reg_alpha":         0.5,     # ↑ aumentado de 0.2 para L1 regularization (reduce complejidad)
    "reg_lambda":        0.8,     # ↑ aumentado de 0.4 para L2 regularization (reduce extremos)
    "random_state":      42,
    "n_jobs":            -1,
    "verbose":           -1,
}

# Params específicos para modelos binarios (O/U, BTTS) — fuerte regularización para señal débil
LGBM_BINARY = {
    **LGBM_BASE,
    "num_leaves":        20,
    "min_child_samples": 50,
    "learning_rate":     0.01,
    "colsample_bytree":  0.55,
    "reg_alpha":         0.5,
    "reg_lambda":        1.0,
}

EARLY_STOPPING_ROUNDS = 150    # más paciencia con lr más bajo


def _cat_features_present(features: list[str]) -> list[str]:
    return [f for f in CATEGORICAL_FEATURES if f in features]


# ---------------------------------------------------------------------------
# Entrenamiento con early stopping
# ---------------------------------------------------------------------------

def _fit_with_early_stopping(
    model: lgb.LGBMClassifier,
    train: pd.DataFrame,
    features: list[str],
    target: str,
) -> lgb.LGBMClassifier:
    """
    Usa el último 15% del train (ordenado por fecha) como validación
    para early stopping. No toca el test set.
    """
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
    return model


def train_1x2(train: pd.DataFrame, features: list[str]) -> lgb.LGBMClassifier:
    # AJUSTE 2026-05-13: class_weight="balanced" para remediar subestimación de empates
    # Síntoma: 92.8% de fallos eran draws (13/14). El modelo era demasiado extremo en 1X2.
    # class_weight="balanced" pondera inversamente a la frecuencia de cada clase.
    # Así el modelo penaliza más los errores en empates (clase menos frecuente).
    model = lgb.LGBMClassifier(
        **LGBM_BASE,
        objective="multiclass",
        num_class=3,
        class_weight="balanced",
        metric="multi_logloss",
    )
    return _fit_with_early_stopping(model, train, features, "result")


def train_binary(train: pd.DataFrame, features: list[str], target: str) -> lgb.LGBMClassifier:
    model = lgb.LGBMClassifier(
        **LGBM_BINARY,
        objective="binary",
        metric="binary_logloss",
    )
    return _fit_with_early_stopping(model, train, features, target)


# ---------------------------------------------------------------------------
# Evaluación
# ---------------------------------------------------------------------------

def evaluate_1x2(
    model: lgb.LGBMClassifier,
    data: pd.DataFrame,
    features: list[str],
    split_label: str = "Test",
) -> float:
    """Evalúa el modelo 1X2. Devuelve el RPS para comparar train vs test."""
    X = data[features]
    y = data["result"].values
    probs = model.predict_proba(X)
    preds = np.argmax(probs, axis=1)

    acc          = accuracy_score(y, preds)
    ll           = log_loss(y, probs)
    rps          = rps_score(y, probs)
    baseline_rps = rps_score(y, np.tile([1/3, 1/3, 1/3], (len(y), 1)))

    print(f"\n  Modelo 1X2 [{split_label} — {len(data)} partidos]")
    print(f"    Accuracy:          {acc:.3f}  (baseline aleatorio ≈ 0.333)")
    print(f"    Log Loss:          {ll:.4f}")
    print(f"    RPS:               {rps:.4f}  (baseline = {baseline_rps:.4f} | mejora: {(baseline_rps - rps) / baseline_rps:.1%})")

    # Brier y calibración por clase
    class_labels = ["Local win", "Empate   ", "Away win "]
    print(f"\n    Calibración y Brier por clase:")
    print(f"    {'':12}  {'Freq.real':>10}  {'Prob.media':>10}  {'Brier':>7}  {'Brier base':>10}")
    for i, lbl in enumerate(class_labels):
        y_bin      = (y == i).astype(float)
        freq_real  = y_bin.mean()
        prob_media = probs[:, i].mean()
        brier      = brier_score_loss(y_bin, probs[:, i])
        brier_base = freq_real * (1 - freq_real)
        marker     = "  ← peor calibrado" if brier > max(brier_score_loss((y == j).astype(float), probs[:, j]) for j in range(3) if j != i) else ""
        print(f"      {lbl}   {freq_real:>9.1%}  {prob_media:>10.1%}  {brier:>7.4f}  {brier_base:>10.4f}{marker}")

    if split_label == "Test":
        _print_confusion(y, preds)

    return rps


def evaluate_binary(
    model: lgb.LGBMClassifier,
    data: pd.DataFrame,
    features: list[str],
    target: str,
    label: str,
    split_label: str = "Test",
) -> float:
    """Evalúa un modelo binario. Devuelve AUC para comparar train vs test."""
    X = data[features]
    y = data[target].values
    probs = model.predict_proba(X)[:, 1]
    preds = (probs >= 0.5).astype(int)

    acc        = accuracy_score(y, preds)
    auc        = roc_auc_score(y, probs)
    brier      = brier_score_loss(y, probs)
    ll         = log_loss(y, probs)
    freq_real  = y.mean()
    prob_media = probs.mean()

    print(f"\n  Modelo {label} [{split_label} — {len(data)} partidos]")
    print(f"    Accuracy:          {acc:.3f}")
    print(f"    AUC-ROC:           {auc:.4f}  (aleatorio = 0.500)")
    print(f"    Brier Score:       {brier:.4f}  (base = {freq_real*(1-freq_real):.4f})")
    print(f"    Log Loss:          {ll:.4f}")
    print(f"    Calibración:       freq.real={freq_real:.1%}  prob.media={prob_media:.1%}")

    return auc


def _print_confusion(y_true, y_pred):
    labels = ["Local", "Empate", "Visit."]
    print(f"\n    Matriz de confusión (filas=real, cols=pred):")
    print(f"    {'':>8} " + "  ".join(f"{l:>7}" for l in labels))
    for i, lbl in enumerate(labels):
        row   = [sum((y_true == i) & (y_pred == j)) for j in range(3)]
        total = sum(y_true == i)
        print(f"    {lbl:>8} " + "  ".join(f"{v:>7}" for v in row) + f"   ({total} total)")

    # DIAGNÓSTICO ESPECÍFICO: Análisis de empates (clase 1)
    draws_real = sum(y_true == 1)
    draws_pred = sum(y_pred == 1)
    draws_correct = sum((y_true == 1) & (y_pred == 1))
    if draws_real > 0:
        draw_recall = draws_correct / draws_real
        print(f"\n    📊 ANÁLISIS DE EMPATES (clase 1):")
        print(f"       Empates reales:     {draws_real}")
        print(f"       Empates predichos:  {draws_pred}")
        print(f"       Empates acertados:  {draws_correct}")
        print(f"       Recall (recall=correct/real): {draw_recall:.1%}")
        if draw_recall < 0.4:
            print(f"       ⚠️  CRÍTICO: Modelo subestima empates. Considera incrementar class_weight.")


def print_top_features(model: lgb.LGBMClassifier, features: list[str], n: int = 15) -> None:
    imp = pd.Series(model.feature_importances_, index=features)
    top = imp.nlargest(n)
    print(f"\n  Top {n} features por importancia:")
    for feat, val in top.items():
        bar = "█" * int(val / top.max() * 20)
        print(f"    {feat:<48} {bar}")


# ---------------------------------------------------------------------------
# Persistencia
# ---------------------------------------------------------------------------

def save_model(model: lgb.LGBMClassifier, name: str, metadata: dict) -> None:
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
    p.add_argument("--input",        type=Path, default=INPUT_FILE)
    p.add_argument("--test-seasons", type=int,  nargs="+", default=[])
    p.add_argument("--skip-btts",    action="store_true")
    return p.parse_args()


def main():
    args = parse_args()

    print(f"Cargando dataset: {args.input}")
    if not args.input.exists():
        print(f"ERROR: no se encuentra {args.input}. Ejecuta feature_engineering.py primero.")
        sys.exit(1)

    df       = load_dataset(args.input)
    df       = encode_categoricals(df, list(df.columns))
    features = get_feature_columns(df)

    print(f"  → {len(df)} partidos  |  {len(features)} features")

    train, test = time_split(df, args.test_seasons)
    print(f"  → Train: {len(train)} partidos  |  Test: {len(test)} partidos")
    if not args.test_seasons:
        print(f"  → Test desde: {test['match_date'].min().strftime('%Y-%m-%d')}")

    meta_base = {
        "features":    features,
        "train_size":  len(train),
        "test_size":   len(test),
        "train_start": str(train["match_date"].min()),
        "train_end":   str(train["match_date"].max()),
        "test_start":  str(test["match_date"].min()),
        "test_end":    str(test["match_date"].max()),
    }

    print(f"\n{'='*55}")

    print("\n[1/3] Entrenando modelo 1X2...")
    m1x2 = train_1x2(train, features)
    rps_tr = evaluate_1x2(m1x2, train, features, split_label="Train")
    rps_te = evaluate_1x2(m1x2, test,  features, split_label="Test")
    overfit_gap = rps_tr - rps_te
    print(f"\n    Overfitting check (RPS):  Train={rps_tr:.4f}  Test={rps_te:.4f}  gap={overfit_gap:+.4f}"
          + ("  ⚠ posible overfitting" if overfit_gap < -0.015 else "  ✓ OK"))
    print_top_features(m1x2, features)
    save_model(m1x2, "lgbm_1x2", {**meta_base, "target": "result", "classes": [0, 1, 2]})

    print("\n[2/3] Entrenando modelo Over/Under 2.5...")
    mou = train_binary(train, features, "over25")
    auc_ou_tr = evaluate_binary(mou, train, features, "over25", "Over/Under 2.5", split_label="Train")
    auc_ou_te = evaluate_binary(mou, test,  features, "over25", "Over/Under 2.5", split_label="Test")
    print(f"\n    Overfitting check (AUC):  Train={auc_ou_tr:.4f}  Test={auc_ou_te:.4f}  gap={auc_ou_tr - auc_ou_te:+.4f}"
          + ("  ⚠ posible overfitting" if auc_ou_tr - auc_ou_te > 0.04 else "  ✓ OK"))
    print_top_features(mou, features, n=10)
    save_model(mou, "lgbm_ou25", {**meta_base, "target": "over25"})

    if not args.skip_btts:
        print("\n[3/3] Entrenando modelo BTTS...")
        mbtts = train_binary(train, features, "btts")
        auc_bt_tr = evaluate_binary(mbtts, train, features, "btts", "BTTS", split_label="Train")
        auc_bt_te = evaluate_binary(mbtts, test,  features, "btts", "BTTS", split_label="Test")
        print(f"\n    Overfitting check (AUC):  Train={auc_bt_tr:.4f}  Test={auc_bt_te:.4f}  gap={auc_bt_tr - auc_bt_te:+.4f}"
              + ("  ⚠ posible overfitting" if auc_bt_tr - auc_bt_te > 0.04 else "  ✓ OK"))
        print_top_features(mbtts, features, n=10)
        save_model(mbtts, "lgbm_btts", {**meta_base, "target": "btts"})

    print(f"\n{'='*55}")
    print("Completado. Siguiente paso → Fase 4: predict_api.py")


if __name__ == "__main__":
    main()
