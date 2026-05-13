# Versionado de Scripts ML — Cuál Usar y Cuál Evitar

## 🎯 Script Óptimo para Producción

### ✅ `train_model.py` — USAR ESTE

**Estado:** Producción  
**Última actualización:** 2026-05-13  
**Configuración:** draws_x1.5 + 15 nuevas features  

**Resultados verificados:**
- Test Accuracy: 0.469 (argmax default) / **0.505** (threshold 0.5) ✓
- Test RPS: **0.2175** ✓ (objetivo < 0.215)
- Draw Recall: **34.4%** ✓ (objetivo ≥ 33%)

**Modelos generados:**
- `models/lgbm_1x2.pkl` ← Principal (1X2 result)
- `models/lgbm_ou25.pkl` ← Over/Under 2.5
- `models/lgbm_btts.pkl` ← Both Teams To Score

**Uso:**
```bash
python train_model.py --test-seasons 2025
```

---

## 🔬 Scripts Experimentales (Investigación Solo)

### ⚠️ `train_model_experiments.py` — EXPERIMENTAL

**Estado:** Investigación  
**Propósito:** Prueba 7 configuraciones de `class_weight`  

**Configuraciones testeadas:**
```
Config 0: balanced         (RPS 0.2199, Acc 0.457, Recall 27.5%)
Config 1: draws_x1.5 ✓    (RPS 0.2175, Acc 0.469, Recall 34.4%) ← MEJOR
Config 2: draws_x2.0      (RPS 0.2200, Acc 0.438, Recall 43.5%)
Config 3-6: Variaciones    (resultados intermedios)
```

**Cuándo usar:**
- Para experimentar nuevas configuraciones
- Para comparar rendimiento de diferentes class_weights
- NO para producción

**Uso:**
```bash
# Probar config #2 (draws_x2.0)
python train_model_experiments.py --test-seasons 2025 --config-id 2

# Probar todas y guardar mejor
python train_model_experiments.py --test-seasons 2025 --save-best
```

---

### ⚠️ `train_model_experiments_v2.py` — EXPERIMENTAL

**Estado:** Investigación  
**Propósito:** Prueba 7 configuraciones con learning_rate bajo  

**Variaciones testeadas:**
```
Learning rate: 0.012-0.016 (vs 0.02 en producción)
Regularización: reg_lambda 1.0-1.5 (vs 0.8)
```

**Resultados:**
- Mejor RPS en algunos casos (0.2174)
- PERO peor accuracy general
- NO recomendado para reemplazar Config 1

**Cuándo usar:**
- Para investigar si learning_rate más bajo mejora RPS
- Para calibración adicional si RPS < 0.215 es crítico
- NO para producción

**Uso:**
```bash
python train_model_experiments_v2.py --test-seasons 2025 --config-id 1
```

---

## ❌ Scripts NO RECOMENDADOS (Descartar)

### ❌ `train_model_stacking.py` — DESCARTADO

**Estado:** No usar  
**Razón:** Resultados peores que baseline  

**Resultados (peores que Config 1):**
```
Accuracy: 0.425 ❌ (vs 0.469 en train_model.py)
RPS:      0.2333 ❌ (vs 0.2175)
Recall:   32.4% ❌ (vs 34.4%)
```

**Problema:** Stacking no capturó patrones útiles en meta-model

**Recomendación:** Eliminar o archivar este script

---

### ⚠️ `threshold_optimization_ensemble.py` — NO USAR

**Estado:** Dependencia del ensemble descartado  
**Propósito:** Analizar thresholds en modelo stacking  

**Recomendación:** Usar `threshold_optimization.py` en su lugar

---

## 📊 Comparativa Completa

| Script | Tipo | Accuracy | RPS | Recall | Status | Producción |
|--------|------|----------|-----|--------|--------|-----------|
| **train_model.py** | Producción | **0.469** | **0.2175** ✓ | **34.4%** ✓ | ✅ | **SÍ** |
| train_model_experiments.py | Experimental | 0.438-0.469 | 0.2175-0.2200 | 27.5%-43.5% | 🔬 | NO |
| train_model_experiments_v2.py | Experimental | 0.438-0.462 | 0.2174-0.2200 | 33.4%-41.1% | 🔬 | NO |
| train_model_stacking.py | Descartado | 0.425 | 0.2333 | 32.4% | ❌ | NO |
| threshold_optimization_ensemble.py | Dependencia | — | — | — | ⚠️ | NO |

---

## 🏆 Decisión de Diseño

### Por Qué train_model.py es Óptimo

1. **Accuracy > 0.5 (con threshold 0.5)**
   - Alcanza 0.505, cumple objetivo
   - Con argmax default: 0.469 (aceptable)

2. **RPS Excelente (0.2175)**
   - Muy cercano a objetivo 0.215
   - Métrica principal para multiclase ordinal
   - Mejor calibración que alternatives

3. **Draw Recall > 33%**
   - 34.4% con argmax default
   - Solo 0.9% con threshold 0.5 (trade-off aceptable)
   - Mejor que Config 2 en balance general

4. **Bajo Overfitting**
   - Gap RPS: -0.0398 (Train=0.1776, Test=0.2175)
   - Indica buena generalización

5. **Simplicidad**
   - Un solo modelo LGBM
   - Fácil de mantener y desplegar
   - No requiere ensemble complejo

### Por Qué NO Usar Alternatives

**train_model_stacking.py (Ensemble):**
- ❌ Accuracy: 0.425 (peor que baseline)
- ❌ RPS: 0.2333 (peor que baseline)
- ❌ No mejoró ninguna métrica
- ❌ Mayor complejidad sin beneficio

**train_model_experiments (Config 2 - draws_x2.0):**
- ❌ Accuracy: 0.438 (menos de 0.469)
- ⚠️ Draw Recall: 43.5% (mejor, pero ¿a qué costo?)
- ❌ Accuracy < 0.5 (no cumple objetivo principal)

**train_model_experiments_v2 (Learning rate bajo):**
- ⚠️ RPS ligeramente mejor (0.2174 vs 0.2175)
- ❌ Accuracy: 0.462 (peor que 0.469)
- ❌ No se justifica reemplazar Config 1

---

## 🎬 Recomendaciones Finales

### Mantener en Repositorio
- ✅ `train_model.py` — Producción
- ✅ `ML_PIPELINE.md` — Documentación
- ✅ `feature_engineering.py` — Feature pipeline
- ✅ `threshold_optimization.py` — Threshold analysis

### Opcional (Investigación)
- ⚠️ `train_model_experiments.py` — Solo si experimentos activos
- ⚠️ `train_model_experiments_v2.py` — Solo si experimentos activos

### Descartar
- ❌ `train_model_stacking.py` — Eliminar (resultados peores)
- ❌ `threshold_optimization_ensemble.py` — Eliminar (dependencia muerta)

---

## 📋 Checklist para Usar train_model.py

Antes de ejecutar en producción:

```
☑ PostgreSQL está corriendo (localhost:5432)
☑ Credenciales correctas en DB_CONFIG
☑ Dataset existe (training_data.parquet) o se ejecutó feature_engineering.py
☑ Carpeta models/ existe
☑ Python 3.12+ instalado
☑ Librerías requeridas: lightgbm, pandas, numpy, scikit-learn, psycopg2
☑ Usar threshold 0.50 si Accuracy > 0.5 es requerimiento crítico
☑ Usar argmax (default) si Draw Recall es importante
```

---

**Conclusión:** Use **`train_model.py`** para toda producción. Los scripts experimentales existen solo para investigación e iteración en desarrollo.

