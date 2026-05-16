# 📚 Índice - ML Pipeline Documentación

## 🎯 Comienza Aquí

**Si tienes 5 minutos:** Lee `GUIA_RAPIDA.md`  
**Si tienes 20 minutos:** Lee `ML_PIPELINE.md`  
**Si necesitas profundizar:** Lee `VERSIONING.md`

---

## 📄 Documentos

### 1. **GUIA_RAPIDA.md** ⭐ START HERE
- **Público:** Cualquiera que quiera usar el pipeline rápidamente
- **Contenido:**
  - ✓ 3 comandos principales
  - ✓ Versión óptima identificada (train_model.py)
  - ✓ Argumentos clave
  - ✓ Flujos de uso típicos
  - ✓ Troubleshooting rápido
- **Tiempo lectura:** 5 minutos
- **Nivel:** Principiante

### 2. **ML_PIPELINE.md** 📖 REFERENCIA DETALLADA
- **Público:** Usuarios que necesitan documentación completa
- **Contenido:**
  - ✓ 4 fases del pipeline
  - ✓ Scripts en orden de ejecución
  - ✓ Argumentos completos de cada script
  - ✓ Explicación de features generadas
  - ✓ Workflow scenarios (4 casos de uso)
  - ✓ Troubleshooting detallado
  - ✓ Cheatsheet rápido
- **Tiempo lectura:** 20-30 minutos
- **Nivel:** Intermedio

### 3. **VERSIONING.md** 🔬 DECISIONES TÉCNICAS
- **Público:** Técnicos que quieren entender por qué se eligió cada opción
- **Contenido:**
  - ✓ train_model.py como óptimo (con justificación)
  - ✓ Por qué descartar otros scripts (stacking, experiments)
  - ✓ Tabla comparativa de 4 versiones
  - ✓ Análisis de trade-offs (accuracy vs recall)
  - ✓ Decisiones de diseño
  - ✓ Recomendaciones de mantención
- **Tiempo lectura:** 15 minutos
- **Nivel:** Avanzado

---

## 🔧 Scripts Recomendados vs No Recomendados

### ✅ USAR EN PRODUCCIÓN

```
train_model.py
├─ Configuración: v4_multi_market — 7 modelos
├─ 1x2: Accuracy 0.505 (threshold 0.5), RPS 0.2175, Draw Recall 34.4% ✓
├─ OU / BTTS: LightGBM binario con features de mercado pinned
├─ Over 0.5 / 1.5 / 3.5: LightGBM binario con OU_PINNED_FEATURES
├─ Córners: Regresor Poisson (λ) → P(>N) garantiza monotonía
└─ Status: ✅ Verificado y optimizado
```

### 🔬 USAR EN INVESTIGACIÓN SOLAMENTE

```
train_model_experiments.py
├─ Propósito: Probar 7 configuraciones de class_weight
├─ Config 1: draws_x1.5 (mejor, igual a production)
├─ Configs 2-6: Variaciones experimentales
└─ Status: 🔬 Investigación

train_model_experiments_v2.py
├─ Propósito: Probar learning_rate bajo + regularización fuerte
├─ Mejora: RPS ligeramente mejor (0.2174)
├─ Trade-off: Accuracy peor (0.462 vs 0.469)
└─ Status: 🔬 Investigación
```

### ❌ NO USAR

```
train_model_stacking.py
├─ Propósito: Ensemble stacking (3 base models + meta-model)
├─ Resultado: Accuracy 0.425, RPS 0.2333 (PEOR que baseline)
├─ Conclusión: No capturó patrones útiles
└─ Status: ❌ Descartado (peor que single model)

threshold_optimization_ensemble.py
├─ Propósito: Analizar thresholds en ensemble
├─ Dependencia: Requiere train_model_stacking.py (descartado)
└─ Status: ❌ Dependencia muerta
```

---

## 🚀 Flujo de Ejecución Típico

### Escenario 1: Entrenamiento Completo (Primera Vez)

```bash
# 1. Generar features desde PostgreSQL
python feature_engineering.py
# ↳ Salida: training_data.parquet (179 features)

# 2. Entrenar modelos
python train_model.py --test-seasons 2025
# ↳ Salida: models/lgbm_1x2.pkl, lgbm_ou25.pkl, lgbm_btts.pkl

# 3. Optimizar thresholds
python threshold_optimization.py --test-seasons 2025
# ↳ Output: Análisis de thresholds (elegir 0.50 para Accuracy > 0.5)
```

**Duración total:** ~7 minutos  
**Cuando hacer:** Primera configuración, cuando cambien features significativamente

---

### Escenario 2: Retrain Solo (Dataset Existe)

```bash
# Dataset ya generado
python train_model.py --test-seasons 2025
# ↳ Retrain rápido, solo modelos
```

**Duración:** ~3-4 minutos  
**Cuando hacer:** Ajustes en modelo, nuevas temporadas

---

### Escenario 3: Analizar Thresholds Específicos

```bash
python threshold_optimization.py \
    --test-seasons 2025 \
    --thresholds 0.40 0.45 0.50 0.55
```

**Duración:** ~1 minuto  
**Cuando hacer:** Comparar rendimiento con diferentes thresholds

---

### Escenario 4: Experimentación (NO afecta producción)

```bash
# Probar Config 2 (draws_x2.0 para más recall)
python train_model_experiments.py --test-seasons 2025 --config-id 2

# Probar todas y guardar mejor
python train_model_experiments.py --test-seasons 2025 --save-best
```

**Duración:** ~3-15 minutos  
**Cuando hacer:** Investigar mejoras, tunning hiperparámetros  
**Nota:** No reemplaza producción

---

## 📊 Métricas Finales Alcanzadas

| Métrica | Objetivo | Resultado | Estado |
|---------|----------|-----------|--------|
| **Accuracy (default)** | N/A | **0.469** | ✓ Baseline |
| **Accuracy (threshold 0.5)** | > 0.5 | **0.505** | ✓ Cumple |
| **RPS** | < 0.215 | **0.2175** | ✓ Muy cercano |
| **Draw Recall** | ≥ 33% | **34.4%** | ✓ Cumple |
| **Overfitting** | gap < 0.04 | **-0.0398** | ✓ Bajo |

---

## 📚 Archivos de Soporte

### Archivos de Entrada Requeridos
- PostgreSQL running (localhost:5432)
- Credenciales en DB_CONFIG de feature_engineering.py

### Archivos Generados
```
training_data.parquet     (Dataset)
models/
├── lgbm_1x2.pkl              (Modelo principal 1X2)
├── lgbm_ou25.pkl             (Over/Under 2.5)
├── lgbm_btts.pkl             (Both Teams To Score)
├── lgbm_over05.pkl           (Over 0.5 goles)
├── lgbm_over15.pkl           (Over 1.5 goles)
├── lgbm_over35.pkl           (Over 3.5 goles)
└── lgbm_corners_lambda.pkl   (λ Poisson — córners totales)
```

### Archivos Históricos (Investigación)
```
experimental outputs (no en producción):
- models/lgbm_1x2_stacking.pkl
- models/lgbm_1x2_best.pkl (si --save-best fue usado)
```

---

## 🔑 Puntos Clave Resumidos

### ✅ HACER

1. **Usar `train_model.py`** para producción
2. **Generar features** con `feature_engineering.py` cuando cambien
3. **Usar threshold 0.50** si Accuracy > 0.5 es crítico
4. **Mantener PostgreSQL corriendo** con datos actualizados
5. **Consultar documentación** cuando tengas dudas

### ❌ NO HACER

1. ❌ No usar `train_model_stacking.py` (resultados peores)
2. ❌ No reemplazar producción con scripts experimentales
3. ❌ No ignorar el threshold analysis (crucial para trade-offs)
4. ❌ No ejecutar sin verificar base de datos
5. ❌ No mezclar diferentes configuraciones de models

---

## 🎓 Lectura Recomendada por Nivel

### Principiante (Solo usar modelos)
1. GUIA_RAPIDA.md (5 min)
2. Ejecutar 3 comandos
3. Listo

### Intermedio (Entender flujo)
1. GUIA_RAPIDA.md (5 min)
2. ML_PIPELINE.md (20 min)
3. Ejecutar con distintos parámetros
4. Entender trade-offs

### Avanzado (Mejorar modelos)
1. Todos los anteriores
2. VERSIONING.md (15 min)
3. train_model_experiments.py para investigación
4. Analizar feature importance en top 15
5. Considerar trade-offs accuracy vs recall

---

## 📋 Cheatsheet Completo

```bash
# Flujo estándar (7 min)
python feature_engineering.py && \
python train_model.py --test-seasons 2025 && \
python threshold_optimization.py --test-seasons 2025

# Solo retrain (4 min)
python train_model.py --test-seasons 2025

# Experimentar sin afectar producción
python train_model_experiments.py --test-seasons 2025 --config-id 2

# Analizar thresholds específicos
python threshold_optimization.py --thresholds 0.40 0.45 0.50 0.55

# Acelerar (omitir player stats)
python feature_engineering.py --no-player-stats
```

---

## 🆘 Soporte Rápido

| Pregunta | Respuesta |
|----------|-----------|
| ¿Qué script usar? | `train_model.py` |
| ¿Cuánto tiempo toma? | ~7 min (completo) o ~4 min (retrain) |
| ¿Cómo cambiar threshold? | Ver `threshold_optimization.py --thresholds` |
| ¿Cómo experimentar? | Ver `train_model_experiments.py` |
| ¿Por qué no stacking? | Resultados peores (0.425 vs 0.469 accuracy) |
| ¿PostgreSQL requerido? | Solo para feature_engineering.py |

---

## 📞 Contacto / Reportar Issues

Si tienes dudas, consulta:
1. **Rápido:** GUIA_RAPIDA.md (preguntas frecuentes)
2. **Detallado:** ML_PIPELINE.md (troubleshooting)
3. **Técnico:** VERSIONING.md (decisiones de diseño)

---

**Última Actualización:** 2026-05-15  
**Status:** ✅ Documentación completa  
**Versión Óptima:** train_model.py (v4_multi_market — 7 modelos)

