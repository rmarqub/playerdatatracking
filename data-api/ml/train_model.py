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
    "num_leaves":        50,      # algo más de capacidad con ~100 features
    "min_child_samples": 20,      # menos restrictivo → captura patrones más finos
    "subsample":         0.8,
    "subsample_freq":    1,
    "colsample_bytree":  0.70,
    "reg_alpha":         0.2,
    "reg_lambda":        0.4,
    "random_state":      42,
    "n_jobs":            -1,
    "verbose":           -1,
}

# Params específicos para modelos binarios (O/U, BTTS) — más simples para señal más débil
LGBM_BINARY = {
    **LGBM_BASE,
    "num_leaves":        31,
    "min_child_samples": 25,
    "learning_rate":     0.015,   # más lento aún — BTTS tiene muy poco signal, necesita más iteraciones
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
    model = lgb.LGBMClassifier(
        **LGBM_BASE,
        objective="multiclass",
        num_class=3,
        class_weight={0: 1.0, 1: 1.8, 2: 1.2},  # boost empates — son los más difíciles
        metric="multi_logloss",
    )
    return _fit_with_early_stopping(model, train, features, "result")


def train_binary(train: pd.DataFrame, features: list[str], target: str) -> lgb.LGBMClassifier:
    model = lgb.LGBMClassifier(
        **LGBM_BINARY,
        objective="binary",
        metric="binary_logloss",
        is_unbalance=True,
    )
    return _fit_with_early_stopping(model, train, features, target)


# ---------------------------------------------------------------------------
# Evaluación
# ---------------------------------------------------------------------------

def evaluate_1x2(model: lgb.LGBMClassifier, test: pd.DataFrame, features: list[str]) -> None:
    X = test[features]
    y = test["result"].values
    probs = model.predict_proba(X)
    preds = np.argmax(probs, axis=1)

    acc          = accuracy_score(y, preds)
    ll           = log_loss(y, probs)
    rps          = rps_score(y, probs)
    baseline_rps = rps_score(y, np.tile([1/3, 1/3, 1/3], (len(y), 1)))

    print(f"\n  Modelo 1X2")
    print(f"    Accuracy:          {acc:.3f}  (baseline aleatorio ≈ 0.333)")
    print(f"    Log Loss:          {ll:.4f}")
    print(f"    RPS:               {rps:.4f}  (baseline = {baseline_rps:.4f})")
    print(f"    RPS mejora:        {(baseline_rps - rps) / baseline_rps:.1%} sobre aleatorio")
    _print_confusion(y, preds)


def evaluate_binary(
    model: lgb.LGBMClassifier,
    test: pd.DataFrame,
    features: list[str],
    target: str,
    label: str,
) -> None:
    X = test[features]
    y = test[target].values
    probs = model.predict_proba(X)[:, 1]
    preds = (probs >= 0.5).astype(int)

    acc   = accuracy_score(y, preds)
    auc   = roc_auc_score(y, probs)
    brier = brier_score_loss(y, probs)
    ll    = log_loss(y, probs)

    print(f"\n  Modelo {label}")
    print(f"    Accuracy:          {acc:.3f}")
    print(f"    AUC-ROC:           {auc:.4f}  (aleatorio = 0.500)")
    print(f"    Brier Score:       {brier:.4f}  (0 = perfecto, 0.25 = aleatorio)")
    print(f"    Log Loss:          {ll:.4f}")


def _print_confusion(y_true, y_pred):
    labels = ["Local", "Empate", "Visit."]
    print(f"\n    Matriz de confusión (filas=real, cols=pred):")
    print(f"    {'':>8} " + "  ".join(f"{l:>7}" for l in labels))
    for i, lbl in enumerate(labels):
        row   = [sum((y_true == i) & (y_pred == j)) for j in range(3)]
        total = sum(y_true == i)
        print(f"    {lbl:>8} " + "  ".join(f"{v:>7}" for v in row) + f"   ({total} total)")


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
    evaluate_1x2(m1x2, test, features)
    print_top_features(m1x2, features)
    save_model(m1x2, "lgbm_1x2", {**meta_base, "target": "result", "classes": [0, 1, 2]})

    print("\n[2/3] Entrenando modelo Over/Under 2.5...")
    mou = train_binary(train, features, "over25")
    evaluate_binary(mou, test, features, "over25", "Over/Under 2.5")
    print_top_features(mou, features, n=10)
    save_model(mou, "lgbm_ou25", {**meta_base, "target": "over25"})

    if not args.skip_btts:
        print("\n[3/3] Entrenando modelo BTTS...")
        mbtts = train_binary(train, features, "btts")
        evaluate_binary(mbtts, test, features, "btts", "BTTS")
        print_top_features(mbtts, features, n=10)
        save_model(mbtts, "lgbm_btts", {**meta_base, "target": "btts"})

    print(f"\n{'='*55}")
    print("Completado. Siguiente paso → Fase 4: predict_api.py")


if __name__ == "__main__":
    main()
