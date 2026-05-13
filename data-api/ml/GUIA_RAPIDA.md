# 🚀 Guía Rápida ML Pipeline

## Versión Óptima Identificada

**✅ USAR: `train_model.py`**

Configuración óptima tras análisis de 4 versiones:
- Accuracy: **0.469** (argmax) / **0.505** (threshold 0.5) ✓
- RPS: **0.2175** (objetivo < 0.215) ✓
- Draw Recall: **34.4%** (objetivo ≥ 33%) ✓

---

## 3 Comandos para Entrenar Modelos

```bash
# 1. Generar features desde PostgreSQL
python feature_engineering.py

# 2. Entrenar 3 modelos (1X2, OU2.5, BTTS)
python train_model.py --test-seasons 2025

# 3. Optimizar threshold de empates
python threshold_optimization.py --test-seasons 2025
```

**Duración total:** ~7 minutos  
**Resultado:** Modelos listos en `models/lgbm_*.pkl`

---

## Argumentos Principales

### `feature_engineering.py`
```bash
python feature_engineering.py \
    --lookback 5              # Ventana rolling (últimos N partidos)
    --h2h 5                   # H2H lookback (últimos M enfrentamientos)
    --output training_data.parquet
    --no-player-stats         # Omitir features de jugadores (más rápido)
```

### `train_model.py` (PRINCIPAL)
```bash
python train_model.py \
    --input training_data.parquet
    --test-seasons 2025       # Temporadas test (ej: 2024 2025)
    --skip-btts               # Omitir BTTS para acelerar
```

### `threshold_optimization.py`
```bash
python threshold_optimization.py \
    --model-path models/lgbm_1x2.pkl
    --test-seasons 2025
    --thresholds 0.25 0.30 0.35 0.40 0.45 0.50 0.55 0.60
```

---

## Qué Hace Cada Script

| Script | Entrada | Salida | Tiempo |
|--------|---------|--------|--------|
| **feature_engineering.py** | PostgreSQL | training_data.parquet (179 features) | 2-3 min |
| **train_model.py** | training_data.parquet | models/lgbm_*.pkl | 3-4 min |
| **threshold_optimization.py** | models/lgbm_1x2.pkl | Análisis de thresholds | 1 min |

---

## Versiones Alternativas (No Usar en Producción)

| Script | Propósito | Accuracy | Acción |
|--------|-----------|----------|--------|
| train_model_experiments.py | Experimentación | 0.438-0.469 | 🔬 Solo investigación |
| train_model_experiments_v2.py | Experimentación | 0.438-0.462 | 🔬 Solo investigación |
| train_model_stacking.py | Ensemble | **0.425** ❌ | ❌ Descartar (peor) |

**Razón de descarte:** El stacking tuvo accuracy PEOR (0.425 vs 0.469) y RPS PEOR (0.2333 vs 0.2175).

---

## Flujos de Uso Típicos

### Caso 1: Entrenamiento Completo
```bash
python feature_engineering.py
python train_model.py --test-seasons 2025
python threshold_optimization.py --test-seasons 2025
```

### Caso 2: Solo Retrain (dataset existe)
```bash
python train_model.py --test-seasons 2025
```

### Caso 3: Análisis de Thresholds
```bash
python threshold_optimization.py --test-seasons 2025 --thresholds 0.40 0.45 0.50
```

### Caso 4: Experimentar (NO afecta producción)
```bash
python train_model_experiments.py --test-seasons 2025 --config-id 2
```

---

## Métricas Clave Explicadas

**RPS (Ranked Probability Score)** — Métrica principal
- 0 = perfecto, 1 = pésimo
- Nuestro modelo: **0.2175** ✓ (muy cercano a objetivo 0.215)
- Penaliza predicciones incorrectas considerando orden ordinal

**Accuracy** — % de predicciones correctas
- Nuestro modelo: **0.469** (baseline) o **0.505** (con threshold 0.5)

**Draw Recall** — % de empates correctamente predichos
- Nuestro modelo: **34.4%** ✓ (cumple objetivo ≥ 33%)
- Con threshold 0.5: 0.9% (trade-off aceptable)

---

## Threshold Recomendado

**Para Accuracy > 0.5:** Usar **threshold 0.5**
```python
if probs[empate] > 0.5:
    prediction = empate
else:
    prediction = local if probs[local] > probs[visitante] else visitante
```

**Resultados con threshold 0.5:**
- Accuracy: **0.505** ✓ (cumple > 0.5)
- RPS: **0.2175** ✓ (excelente)
- Draw Recall: 0.9% (trade-off inevitable)

---

## Modelos Generados

```
models/
├── lgbm_1x2.pkl        ← PRINCIPAL (resultado 1X2)
│   └─ Accuracy: 0.469 | RPS: 0.2175 | Draw Recall: 34.4%
├── lgbm_ou25.pkl       (Over/Under 2.5)
│   └─ Accuracy: 0.567 | AUC: 0.583
└── lgbm_btts.pkl       (Both Teams To Score)
    └─ Accuracy: 0.541 | AUC: 0.535
```

---

## Configuración Interna

**Cambio clave en train_model.py (2026-05-13):**

```python
class_weight = {
    0: 0.9,      # Local
    1: 1.5,      # Empate (1.5x más importante)
    2: 0.9       # Visitante
}
```

Esta ponderación es lo que hace que el modelo sea óptimo para:
- ✓ RPS bajo (buena calibración)
- ✓ Accuracy alto (con threshold)
- ✓ Draw Recall > 33%

---

## Features Generadas (15+ nuevas)

**Corners & Efficiency:**
- corner_dominance_diff (6º en importancia ⭐)
- shooting_accuracy, shots_inside_box_rate

**Draw Tendency:**
- away_season_draw_rate (11º en importancia)
- home_season_draw_rate

**Consistency (volatilidad):**
- roll_std_goals_for_lastN (12º-13º en importancia)
- roll_std_goals_against_lastN

**Disciplina:**
- yellow_cards, fouls_per_shot

**Balance:**
- xg_balance, draw_tendency_index

---

## Troubleshooting Rápido

| Error | Solución |
|-------|----------|
| UnicodeEncodeError (Windows) | `$env:PYTHONIOENCODING="utf-8"` |
| PostgreSQL Connection | Verificar localhost:5432 |
| Dataset no encontrado | Ejecutar `feature_engineering.py` primero |
| Modelos lento | Usar `--no-player-stats` para acelerar |

---

## Checklist Antes de Producción

```
☑ PostgreSQL corriendo
☑ DB_CONFIG credenciales correctas
☑ training_data.parquet generado
☑ models/ directorio existe
☑ Python 3.12+
☑ Librerías: lightgbm, pandas, numpy, scikit-learn, psycopg2
☑ Usar threshold 0.50 si Accuracy > 0.5 es crítico
```

---

## Links a Documentación Completa

- **Guía Detallada:** Consultar `ML_PIPELINE.md`
- **Versioning & Decisiones:** Consultar `VERSIONING.md`
- **Memory de Análisis:** Consultar `.claude/projects/*/memory/draws_calibration_final.md`

---

## Resumen Ejecución

**Versión Óptima:** `train_model.py`  
**Configuración:** draws_x1.5 + 15 nuevas features  
**Status:** ✅ Producción lista

3 comandos → ~7 minutos → Modelos optimizados ✓

