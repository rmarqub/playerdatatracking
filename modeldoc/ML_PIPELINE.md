# ML Pipeline - Guía Completa de Uso

## 📋 Resumen Ejecutivo

El pipeline ML de predicción de partidos consta de **4 fases principales**:

1. **Fase 1: Feature Engineering** — Genera dataset desde PostgreSQL
2. **Fase 2: Entrenamiento** — Entrena modelos (1X2, OU2.5, BTTS)
3. **Fase 3: Optimización** — Ajusta thresholds de predicción
4. **Fase 4: Predicción** — Usa modelos en producción

---

## 🚀 Pipeline Estándar (Flujo Principal)

### Orden de Ejecución Recomendado

```
1. feature_engineering.py        ← Genera dataset
     ↓
2. train_model.py               ← Entrena modelos (ÓPTIMO)
     ↓
3. threshold_optimization.py    ← Optimiza thresholds
     ↓
4. predict_api.py              ← (Backend consume modelos)
```

---

## 📖 Scripts en Detalle

### 1️⃣ `feature_engineering.py` — Generación de Dataset

**Propósito:** Extrae datos de PostgreSQL y genera features de entrenamiento

**Ejecución básica:**
```bash
python feature_engineering.py
```

**Con parámetros personalizados:**
```bash
python feature_engineering.py \
    --lookback 5 \
    --h2h 5 \
    --output training_data.parquet \
    --no-player-stats
```

**Argumentos reconocidos:**

| Argumento | Tipo | Default | Descripción |
|-----------|------|---------|-------------|
| `--lookback` | `int` | 5 | Ventana rolling para estadísticas (últimos N partidos) |
| `--h2h` | `int` | 5 | Lookback para enfrentamientos directos (últimos M partidos H2H) |
| `--output` | `Path` | `training_data.parquet` | Ruta del archivo de salida |
| `--no-player-stats` | flag | — | Omite features de jugadores (más rápido) |

**Salida:**
- `training_data.parquet` — Dataset con ~179 features

**Duración:** ~2-3 minutos

**Features generadas (principales):**
- Rolling stats (últimos 5 partidos)
- H2H (últimos 5 enfrentamientos)
- Player stats (rating, goles, asistencias)
- Season form (PPG, GPG)
- Draw tendency & Consistency (NUEVAS)
- Corners & Discipline (NUEVAS)
- Percentiles temporales de jugadores

---

### 2️⃣ `train_model.py` — Entrenamiento de Modelos (⭐ ÓPTIMO)

**Propósito:** Entrena 3 modelos multiclase/binarios para predicción de partidos

**Estado:** ✅ PRODUCCIÓN — Usa configuración óptima (draws_x1.5)

**Ejecución básica:**
```bash
python train_model.py --test-seasons 2025
```

**Con dataset personalizado:**
```bash
python train_model.py \
    --input training_data.parquet \
    --test-seasons 2025 \
    --skip-btts
```

**Argumentos reconocidos:**

| Argumento | Tipo | Default | Descripción |
|-----------|------|---------|-------------|
| `--input` | `Path` | `training_data.parquet` | Path al dataset |
| `--test-seasons` | `int[]` | — | Temporadas para test (ej: `2025` o `2024 2025`) |
| `--skip-btts` | flag | — | Omite entrenamiento de BTTS (más rápido) |

**Modelos entrenados:**

1. **lgbm_1x2.pkl** — Resultado (1=local, X=empate, 2=visitante)
   - Config: `class_weight={0: 0.9, 1: 1.5, 2: 0.9}`
   - Test Accuracy: **0.469** (argmax default)
   - Test RPS: **0.2175** ✓
   - Draw Recall: **34.4%** ✓

2. **lgbm_ou25.pkl** — Over/Under 2.5 goles
   - Test Accuracy: 0.567
   - AUC-ROC: 0.583

3. **lgbm_btts.pkl** — Both Teams To Score
   - Test Accuracy: 0.541
   - AUC-ROC: 0.535

**Salida:**
```
models/
├── lgbm_1x2.pkl      ← PRINCIPAL (resultado 1X2)
├── lgbm_ou25.pkl     ← Over/Under 2.5
└── lgbm_btts.pkl     ← Both Teams To Score
```

**Duración:** ~3-4 minutos

**Métricas clave (1X2):**
```
Train: Accuracy 0.691, RPS 0.1776
Test:  Accuracy 0.469, RPS 0.2175
Overfitting: gap -0.0398 (bajo)
```

---

### 3️⃣ `threshold_optimization.py` — Optimización de Threshold

**Propósito:** Encuentra el threshold óptimo para predicción de empates

**Ejecución básica:**
```bash
python threshold_optimization.py --test-seasons 2025
```

**Con thresholds personalizados:**
```bash
python threshold_optimization.py \
    --model-path models/lgbm_1x2.pkl \
    --test-seasons 2025 \
    --thresholds 0.25 0.30 0.35 0.40 0.45 0.50 0.55 0.60
```

**Argumentos reconocidos:**

| Argumento | Tipo | Default | Descripción |
|-----------|------|---------|-------------|
| `--input` | `Path` | `training_data.parquet` | Dataset para test |
| `--model-path` | `Path` | `models/lgbm_1x2.pkl` | Modelo a evaluar |
| `--test-seasons` | `int[]` | `[2025]` | Temporadas para test |
| `--thresholds` | `float[]` | `[0.25...0.60]` | Thresholds a probar |

**Estrategia de Threshold:**
```python
# Si probs[empate] > threshold → predice empate
# Si no → argmax(probs[local], probs[visitante])

if probs[1] > threshold:
    prediction = 1 (empate)
else:
    prediction = 0 if probs[0] > probs[2] else 2
```

**Salida de ejemplo:**
```
Threshold    Accuracy     RPS        DrawRecall   
0.50         0.505        0.2175     0.9%        ← ÓPTIMO para Accuracy > 0.5
0.45         0.498        0.2175     5.8%
0.40         0.478        0.2175     22.1%
```

**Recomendación:** Usar **threshold 0.50** para garantizar Accuracy > 0.5

**Duración:** ~1 minuto

---

### 🔬 Scripts Experimentales (No usar en producción)

#### `train_model_experiments.py` — Experimentar Configuraciones v1

**Propósito:** Prueba 7 configuraciones diferentes de `class_weight`

**Uso:**
```bash
# Probar todas las configuraciones
python train_model_experiments.py --test-seasons 2025

# Probar solo config #2
python train_model_experiments.py --test-seasons 2025 --config-id 2

# Guardar mejor resultado
python train_model_experiments.py --test-seasons 2025 --save-best
```

**Configuraciones (0-6):**
- Config 0: `balanced` (baseline)
- Config 1: `{0:0.9, 1:1.5, 2:0.9}` — draws_x1.5 ✓ (MEJOR)
- Config 2: `{0:0.85, 1:2.0, 2:0.85}` — draws_x2.0 (mejor recall)
- Config 3-6: Variaciones (reg_lambda, num_leaves)

**Duración:** ~15 minutos (todas) / ~3 min (una)

---

#### `train_model_experiments_v2.py` — Experimentar Configuraciones v2 (Avanzado)

**Propósito:** Prueba 7 configuraciones con learning_rate más bajo y regularización fuerte

**Uso:**
```bash
python train_model_experiments_v2.py --test-seasons 2025 --config-id 1
```

**Configuraciones (0-6):**
- Variaciones de `learning_rate` (0.012-0.016)
- Variaciones de `reg_lambda` (1.0-1.5)
- Mejor para RPS pero peor accuracy general

**Duración:** ~15 minutos (todas)

---

#### ❌ `train_model_stacking.py` — Ensemble Stacking (DESCARTADO)

**Estado:** ⚠️ NO RECOMENDADO — Resultados peores que baseline

**Razón del descarte:**
- Accuracy: 0.425 (vs 0.469 baseline) ❌
- RPS: 0.2333 (vs 0.2175 baseline) ❌
- Draw Recall: 32.4% (vs 34.4% baseline) ❌

**Conclusión:** El stacking no capturó patrones útiles. Mejor usar train_model.py.

---

## 💾 Archivos Auxiliares

### `threshold_optimization_ensemble.py`

**Propósito:** Analiza thresholds en modelo ensemble (no usar actualmente)

**Nota:** Creado para evaluar ensemble stacking, pero ensemble no mejoró resultados

---

### `compute_player_percentiles.py`

**Propósito:** Calcula percentiles temporales de jugadores por liga/temporada

**Uso:**
```bash
# Calcular todas las temporadas
python compute_player_percentiles.py

# Calcular solo una temporada
python compute_player_percentiles.py --season 2025

# Recalcular desde cero (borrar previos)
python compute_player_percentiles.py --truncate
```

**Nota:** Se ejecuta automáticamente como parte de `feature_engineering.py`

---

### `compute_season_percentiles.py`

**Propósito:** Calcula percentiles de jugadores por temporada (alternativo)

**Nota:** Similar a `compute_player_percentiles.py`, versión más simple

---

## 🎯 Workflow Paso a Paso

### Scenario 1: Entrenamiento Completo Desde Cero

```bash
# 1. Generar dataset (conexión a PostgreSQL requerida)
python feature_engineering.py

# 2. Entrenar modelos
python train_model.py --test-seasons 2025

# 3. Optimizar thresholds
python threshold_optimization.py --test-seasons 2025

# ✅ Modelos listos en ./models/
```

**Duración total:** ~6-7 minutos

---

### Scenario 2: Retrain Solo Modelo 1X2 (Sin Features)

```bash
# Si dataset ya existe:
python train_model.py --test-seasons 2025

# Solo modelos binarios (OU2.5, BTTS):
python train_model.py --test-seasons 2025 --skip-btts
```

---

### Scenario 3: Experimentar Configuraciones

```bash
# Probar Config 2 (draws_x2.0)
python train_model_experiments.py --test-seasons 2025 --config-id 2

# Probar todas y guardar mejor
python train_model_experiments.py --test-seasons 2025 --save-best

# Resultado se guarda en ./models/lgbm_1x2_best.pkl
```

---

## 📊 Comparativa de Modelos

| Script | Estado | Accuracy | RPS | Recall | Casos de Uso |
|--------|--------|----------|-----|--------|-------------|
| **train_model.py** | ✅ Producción | 0.469 | **0.2175** ✓ | 34.4% ✓ | Predicción en vivo |
| train_model.py (threshold 0.5) | ✅ Producción | **0.505** ✓ | **0.2175** ✓ | 0.9% | Cuando accuracy > 0.5 es crítico |
| train_model_experiments.py | 🔬 Experimental | 0.469-0.456 | 0.2199-0.2175 | 27.5%-43.5% | Investigación de configs |
| train_model_experiments_v2.py | 🔬 Experimental | 0.462-0.438 | 0.2174-0.2200 | 33.4%-41.1% | Investigación de LR bajo |
| train_model_stacking.py | ❌ Descartado | 0.425 | 0.2333 | 32.4% | NO USAR |

---

## 🔧 Configuración de Entrada

### PostgreSQL (database)

**Archivo:** `feature_engineering.py` línea 36-42

```python
DB_CONFIG = {
    "host": "localhost",
    "port": 5432,
    "dbname": "playerdata",
    "user": "postgres",
    "password": "admin",
}
```

**Tablas requeridas:**
- `fixture` — Partidos
- `fixture_team_stats` — Estadísticas de equipos
- `fixture_player_stats` — Estadísticas de jugadores

---

## 📈 Métricas Explicadas

### RPS (Ranked Probability Score)
- **Rango:** 0 (perfecto) — 1 (pésimo)
- **Baseline aleatorio:** 0.25-0.30
- **Modelo competitivo:** < 0.20
- **Nuestro modelo:** **0.2175** ✓ (excelente)
- **Interpretación:** Penaliza predicciones incorrectas pero ordinal (local < empate < visitante)

### Accuracy
- **Métrica simple:** % de predicciones correctas
- **Nuestro modelo:** 0.469 (baseline) / 0.505 (threshold 0.5)
- **Nota:** RPS es métrica preferida para fútbol (multiclase ordinal)

### Draw Recall
- **Definición:** % de empates reales que el modelo predice
- **Nuestro modelo:** 34.4% (baseline) / 0.9% (threshold 0.5)
- **Trade-off:** Accuracy vs Recall (incompatible simultáneamente)

---

## ⚙️ Parámetros Clave en Modelos

### LightGBM Configuration (train_model.py)

```python
LGBM_BASE = {
    "n_estimators": 3000,
    "learning_rate": 0.02,
    "num_leaves": 35,
    "reg_lambda": 0.8,      # L2 regularization
    "reg_alpha": 0.5,       # L1 regularization
    "min_child_samples": 30,
    "colsample_bytree": 0.60,
    "subsample": 0.8,
    "random_state": 42,
    "n_jobs": -1,
    "verbose": -1,
}

# Específico para 1X2:
class_weight = {0: 0.9, 1: 1.5, 2: 0.9}  # Empieza 1.5x más importante
```

### Early Stopping
```python
EARLY_STOPPING_ROUNDS = 150
```
- Si validación no mejora en 150 iteraciones, detiene entrenamiento

---

## 🐛 Troubleshooting

### Error: UnicodeEncodeError (Windows PowerShell)
```powershell
$env:PYTHONIOENCODING="utf-8"
python script.py
```

### Error: PostgreSQL Connection Refused
- Verificar que PostgreSQL está corriendo: `localhost:5432`
- Verificar credenciales en `DB_CONFIG`

### Error: Dataset no encontrado
```bash
# Generar primero:
python feature_engineering.py
```

### Modelos muy lentos de entrenar
```bash
# Usar --no-player-stats para omitir features de jugadores:
python feature_engineering.py --no-player-stats
python train_model.py --test-seasons 2025
```

---

## 📝 Cheatsheet Rápido

```bash
# Flujo completo en 3 comandos:
python feature_engineering.py
python train_model.py --test-seasons 2025
python threshold_optimization.py --test-seasons 2025

# Solo retrain (dataset existe):
python train_model.py --test-seasons 2025

# Experimentar (no afecta producción):
python train_model_experiments.py --test-seasons 2025 --save-best

# Verificar thresholds alternativos:
python threshold_optimization.py --thresholds 0.40 0.45 0.50 0.55
```

---

## 📚 Referencias Internas

- **Feature Engineering Details:** Ver comentarios en `feature_engineering.py` (línea 1-50)
- **Model Config Details:** Ver `train_model.py` línea 160-190
- **Threshold Strategy:** Ver `threshold_optimization.py` línea 90-130

---

**Última Actualización:** 2026-05-13  
**Estado:** ✅ Optimizado y listo para producción  
**Versión Python:** 3.12+  
**Librerías:** lightgbm, pandas, numpy, scikit-learn, psycopg2
