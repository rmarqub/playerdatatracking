# Features del Modelo Predictivo — Referencia Completa

El módulo predictivo (`data-api/ml/`) entrena siete modelos LightGBM independientes sobre un dataset de partidos de fútbol. Este documento describe cada feature: su definición, cómo se calcula, para qué sirve y cómo influye en las predicciones.

---

## Arquitectura de predicción

| Modelo | Target | Tipo | Config |
|--------|--------|------|--------|
| `lgbm_1x2` | Resultado: Victoria Local (0), Empate (1), Victoria Visitante (2) | Multiclase | `LGBM_BASE` |
| `lgbm_ou25` | Over/Under 2.5 goles | Binario | `LGBM_BINARY` |
| `lgbm_btts` | Ambos Equipos Anotan (BTTS) | Binario | `LGBM_BINARY` |
| `lgbm_over05` | Al menos 1 gol en el partido | Binario | `LGBM_BINARY` |
| `lgbm_over15` | Al menos 2 goles en el partido | Binario | `LGBM_BINARY` |
| `lgbm_over35` | Más de 3 goles en el partido | Binario | `LGBM_BINARY` |
| `lgbm_corners_lambda` | Córners totales esperados (λ Poisson) | Regresor Poisson | `LGBM_REGRESSOR` |

Los siete modelos comparten el mismo dataset de features, pero cada uno usa un subconjunto protegido (`*_PINNED_FEATURES`) y aprende cuáles son relevantes para su objetivo.

Los modelos de goles (over_X) se entrenan sobre la señal de mercados histórica: el regresor de córners estima λ y luego aplica la distribución Poisson para calcular P(>N) para N=3..10, garantizando monotonía matemática.

---

## Convención de nomenclatura

La mayoría de features aparecen duplicadas con prefijo `home_` / `away_` (o `_h` / `_a`) para el equipo local y visitante. Las features `diff_*` representan la diferencia `home - away`.

---

## 1. Rolling Features (Ventana Móvil)

**Archivo:** `feature_engineering.py`, función `compute_rolling_features()`  
**Ventana por defecto:** últimos 5 partidos (`DEFAULT_LOOKBACK = 5`)  
**Cálculo:** media con `shift(1).rolling(n, min_periods=1).mean()` — el shift evita *data leakage* (el partido actual no se incluye)

Cada columna base genera dos features: `home_roll_<col>_last5` y `away_roll_<col>_last5`.

| Feature base | Significado |
|---|---|
| `goals_for` | Goles marcados por partido |
| `goals_against` | Goles recibidos por partido |
| `won` | Partidos ganados (0/1) |
| `drew` | Partidos empatados (0/1) |
| `lost` | Partidos perdidos (0/1) |
| `scored` | El equipo marcó al menos un gol (0/1) |
| `clean_sheet` | Portería a cero (0/1) |
| `xg_for` | Expected Goals a favor |
| `xg_against` | Expected Goals en contra |
| `shots_on_goal` | Tiros a puerta |
| `shots_total` | Tiros totales |
| `possession` | Posesión (%) |
| `passes_pct` | Precisión de pases (%) |
| `corner_kicks` | Córners a favor |
| `saves` | Paradas del portero |
| `shooting_accuracy` | `shots_on_goal / shots_total` |
| `shots_inside_box_rate` | `shots_inside_box / shots_total` |
| `corners_against` | Córners concedidos (del rival) |
| `corner_ratio` | `corner_kicks / (corner_kicks + corners_against)` — dominio de córners |
| `fouls_per_shot` | Faltas cometidas por tiro a puerta rival |
| `yellow_cards` | Tarjetas amarillas |
| `fouls` | Faltas cometidas |
| `btts` | Ambos equipos anotaron (0/1) — tasa histórica del equipo |
| `ou25` | Partido terminó Over 2.5 (0/1) — tasa histórica |
| `ou15` | Partido terminó Over 1.5 (0/1) — tasa histórica |

**Cómo usa el modelo:** estas features capturan el **estado de forma reciente**. Un equipo con `home_roll_goals_for_last5 = 2.4` ha promediado 2.4 goles en casa en sus últimos 5 partidos. LightGBM las usa tanto individualmente como en combinación con las del rival.

---

## 2. Rolling por Localía (Home/Away Split)

**Archivo:** `feature_engineering.py` + `predict_api.py` (línea 63)  
**Lógica:** como las rolling generales pero calculadas solo sobre partidos jugados de local o de visitante

| Feature | Descripción |
|---|---|
| `roll_goals_for_h_last5` | Media goles marcados jugando de local, últimos 5 partidos en casa |
| `roll_goals_against_h_last5` | Media goles recibidos jugando de local |
| `roll_won_h_last5` | Tasa de victorias jugando de local |
| `roll_xg_for_h_last5` | xG a favor jugando de local |
| `roll_shots_on_goal_h_last5` | Tiros a puerta jugando de local |
| `roll_goals_for_a_last5` | (ídem visitante) |
| `roll_goals_against_a_last5` | (ídem visitante) |
| … | … |

**Cómo usa el modelo:** separan el rendimiento según localía, lo que es crítico porque muchos equipos tienen un perfil muy diferente de local vs visitante.

---

## 3. EMA Features (Media Móvil Exponencial)

**Archivo:** `feature_engineering.py`, función `compute_ema_features()`  
**Span por defecto:** 5  
**Cálculo:** `ewm(span=n, min_periods=1).mean()` con shift

| Feature base | Descripción |
|---|---|
| `ema_goals_for` | Tendencia reciente de goles marcados (más peso a lo reciente) |
| `ema_goals_against` | Tendencia de goles recibidos |
| `ema_won` | Tendencia de victorias |
| `ema_scored` | Tendencia de partidos en que marcó |
| `ema_clean_sheet` | Tendencia de porterías a cero |
| `ema_xg_for` | Tendencia del xG ofensivo |
| `ema_xg_against` | Tendencia del xG defensivo |
| `ema_shots_on_goal` | Tendencia de tiros a puerta |

Prefijos: `home_ema_*` y `away_ema_*`.

**Cómo usa el modelo:** complementan las rolling con mayor sensibilidad a cambios bruscos recientes (una racha de 3 goles esta semana pesa más que la de hace 5 semanas).

---

## 4. Season Form (Forma Acumulada en Temporada)

**Archivo:** `feature_engineering.py`, función `compute_season_form()`  
**Cálculo:** acumulado con `cumsum()` y `shift(1)`, por equipo y temporada

| Feature | Descripción |
|---|---|
| `home_season_ppg` | Puntos por partido del equipo local acumulados en la temporada |
| `home_season_gfpg` | Goles a favor por partido acumulados |
| `home_season_gapg` | Goles en contra por partido acumulados |
| `home_season_games` | Partidos jugados en la temporada |
| `away_season_ppg` | (ídem visitante) |
| … | … |

**Cómo usa el modelo:** indican la trayectoria de la temporada entera, complementando las rolling que solo miran los últimos 5 partidos. Un equipo puede tener mala forma reciente pero buen balance de temporada.

---

## 5. Draw Features (Features Específicas de Empate)

**Archivo:** `feature_engineering.py` + `train_model.py` (`DRAW_PINNED_FEATURES`)  
**Importancia:** estas 14 features están **protegidas** en la selección de features y no pueden ser eliminadas por el selector automático, porque son señales específicas de la clase minoritaria (empate).

| Feature | Cálculo | Significado |
|---|---|---|
| `season_draw_rate` | `empates_temporada / partidos_temporada` (por equipo) | Tendencia empate histórica del equipo en esta temporada |
| `league_draw_rate` | expanding mean de empates en la liga | Tasa base de empates histórica de la liga |
| `league_season_draw_rate` | expanding mean filtrado por temporada | Tasa de empates en esta liga y temporada específica |
| `team_draw_vs_league` | `draw_rate_equipo - draw_rate_liga` | Si el equipo empata más o menos que la media de su liga |
| `draw_tendency_index` | `min(dr_home, dr_away) / max(dr_home, dr_away)` | Qué tan similares son las tendencias de empate de ambos equipos (1 = idénticas) |
| `draw_tendency_diff` | `draw_rate_home - draw_rate_away` | Diferencia cruda de tendencias de empate |
| `both_draw_prone` | `min(dr_home, dr_away)` | El mínimo denominador común de propensión al empate |
| `draw_rate_diff_recent` | `rolling_drew_home - rolling_drew_away` | Diferencia de frecuencia de empates recientes |
| `both_teams_recent_draw_rate` | `(rolling_drew_home + rolling_drew_away) / 2` | Media de empates recientes de ambos equipos |

**Cómo usa el modelo:** los empates son la clase más difícil de predecir (la modelo les da peso extra de 1.22 en el entrenamiento). Estas features ayudan a detectar partidos entre dos equipos conservadores que tienden a igualar.

---

## 6. Goals & Over/Under Features

**Archivo:** `feature_engineering.py`, función `compute_ouc_features()`

| Feature | Cálculo | Uso |
|---|---|---|
| `combined_xg` | `xg_home + xg_away` | Amenaza goleadora combinada del partido |
| `defensive_porosity` | `xga_home + xga_away` | Debilidad defensiva combinada |
| `total_season_goal_rate` | `gfpg_home + gfpg_away` | Goles esperados por partido basados en temporada |
| `goal_threat_product` | `gfpg_home × gfpg_away` | Señal multiplicativa: alta si ambos equipos atacan bien |
| `both_teams_score_rate` | `scored_home × scored_away` | Probabilidad conjunta de que ambos anoten (para BTTS) |
| `clean_sheet_clash` | `cs_home × cs_away` | Probabilidad conjunta de portería a cero (contra-BTTS) |
| `combined_corners` | `home_roll_corner_kicks + away_roll_corner_kicks` | Total de córners esperados — señal principal del regresor Poisson |

**Cómo usa el modelo:** fundamentales para `lgbm_ou25`, `lgbm_btts` y los modelos over_X. Un partido con `combined_xg` alto apunta a Over 2.5; `both_teams_score_rate` alto apunta a BTTS. `combined_corners` es la feature principal del regresor Poisson de córners.

---

## 7. Balance Features (Señales de Equilibrio)

**Archivo:** `feature_engineering.py` (líneas 826-840)

| Feature | Cálculo | Rango | Significado |
|---|---|---|---|
| `xg_balance` | `1 - |xg_home - xg_away| / (xg_home + xg_away)` | 0–1 | 1 = partidos muy equilibrados en xG; 0 = uno domina |
| `ppg_balance` | `min(ppg_h, ppg_a) / max(ppg_h, ppg_a)` | 0–1 | 1 = equipos iguales en puntos; 0 = clara jerarquía |
| `match_balance_index` | `mean(ppg_balance, xg_balance)` | 0–1 | Índice combinado de equilibrio del partido |

**Cómo usa el modelo:** un `match_balance_index` cercano a 1 aumenta la probabilidad de empate. Cuando hay desequilibrio claro, el modelo penaliza la predicción de empate.

---

## 8. Differential Features (Home − Away)

**Archivo:** `feature_engineering.py` (líneas 888-932)  
**Cálculo:** `home_feature - away_feature`  
**Interpretación:** valores positivos indican ventaja local; negativos, ventaja visitante

### 8.1 Diferenciales de Rendimiento

| Feature diff | Base |
|---|---|
| `diff_goals_for` | Goles marcados rolling |
| `diff_goals_against` | Goles recibidos rolling |
| `diff_wins` | Victorias rolling |
| `diff_shots_on_goal` | Tiros a puerta rolling |
| `diff_possession` | Posesión rolling |
| `diff_passes_pct` | Precisión pases rolling |
| `diff_xg` | xG rolling |
| `diff_xga` | xGA rolling |
| `diff_season_ppg` | PPG de temporada |
| `diff_season_gfpg` | Goles a favor/partido temporada |
| `diff_season_gapg` | Goles en contra/partido temporada |

### 8.2 Diferenciales de Estrategia

| Feature diff | Descripción |
|---|---|
| `diff_corners` | Diferencia córners a favor |
| `diff_corners_against` | Diferencia córners en contra |
| `corner_dominance_diff` | Diferencia de dominio en juego aéreo/estratégico |
| `diff_yellow_cards` | Diferencia agresividad táctica |
| `diff_fouls_per_shot` | Diferencia estilo defensivo |
| `diff_shooting_accuracy` | Diferencia eficiencia ofensiva |

### 8.3 Diferenciales de Jugadores

| Feature diff | Descripción |
|---|---|
| `diff_avg_rating` | Diferencia rating promedio de titulares |
| `diff_goals_pstarted` | Diferencia goles por titular |
| `diff_key_passes` | Diferencia pases clave por titular |
| `diff_def_actions` | Diferencia acciones defensivas por titular |
| `diff_att_rating` | Diferencia rating atacantes |
| `diff_def_rating` | Diferencia rating defensas |
| `diff_gk_rating` | Diferencia rating portero |
| `diff_gk_save_pct` | Diferencia % paradas portero |
| `h2h_diff_avg_rating` | Diferencia rating en H2H histórico |

### 8.4 Diferenciales EMA

| Feature diff | Descripción |
|---|---|
| `diff_ema_goals_for` | Diferencia tendencia goles a favor |
| `diff_ema_goals_against` | Diferencia tendencia goles en contra |
| `diff_ema_won` | Diferencia tendencia victorias |
| `diff_ema_scored` | Diferencia tendencia partidos en que marcó |
| `diff_ema_clean_sheet` | Diferencia tendencia porterías a cero |
| `diff_ema_xg` | Diferencia tendencia xG |

### 8.5 Diferenciales de Percentiles de Jugadores

| Feature diff | Descripción |
|---|---|
| `diff_att_goal_pct` | Diferencia percentil goleador atacante principal |
| `diff_top_attacker_pct` | Diferencia percentil mejor atacante |
| `diff_def_pct` | Diferencia percentil defensivo |
| `diff_starter_rating_pct` | Diferencia percentil rating titulares |

**Cómo usa el modelo:** los diferenciales son muy importantes para `lgbm_1x2`. Una diferencia grande de `diff_season_ppg` o `diff_avg_rating` apunta a victoria del equipo superior. LightGBM los combina con features absolutas para capturar tanto el nivel individual como la ventaja relativa.

---

## 9. Player Rolling Features (Estadísticas de Jugadores)

**Archivo:** `feature_engineering.py`, función `compute_player_rolling_features()`  
**Fuente:** tabla `fixture_player_stats` via percentiles precalculados  
**Filtro:** si hay menos de 6 titulares con datos, el partido se marca como NaN para esas features  
**Ventana:** últimos 5 partidos del equipo

| Feature base | Descripción |
|---|---|
| `avg_rating` | Rating promedio de todos los titulares |
| `goals_pstarted` | Goles por titular (producción ofensiva por jugador) |
| `key_passes_pstarted` | Pases clave por titular (creatividad) |
| `def_actions_pstarted` | Tackles + interceptaciones por titular |
| `duel_win_pct` | % duelos ganados por el equipo |
| `gk_avg_rating` | Rating promedio del portero |
| `gk_save_pct` | % de paradas del portero |
| `avg_rating_d` | Rating promedio solo de defensas |
| `avg_rating_m` | Rating promedio solo de centrocampistas |
| `avg_rating_f` | Rating promedio solo de delanteros |
| `max_scorer_goals` | Goles del máximo goleador en la alineación |
| `goals_concentration` | Concentración de goles en pocos jugadores (si 1 jugador anota todos, = 1) |
| `lineup_continuity` | % de jugadores que repiten respecto al último partido |

Prefijos: `home_roll_<feature>_last5` y `away_roll_<feature>_last5`.

**Cómo usa el modelo:** integran la calidad individual de los jugadores que efectivamente juegan. Un equipo con alto `diff_avg_rating` y alta `lineup_continuity` sugiere que la alineación titular es su habitual de mayor nivel.

---

## 10. Percentiles de Jugadores (Lineup Percentiles)

**Archivo:** `compute_season_percentiles.py` + `compute_player_percentiles.py`  
**Fuente:** `player_season_percentiles` (BD) — precalculados sin leakage temporal  
**Agrupación:** por liga y temporada (los percentiles reflejan el rendimiento relativo dentro de la competición)

| Feature | Descripción |
|---|---|
| `home_avg_att_percentile` | Percentil ofensivo promedio de los atacantes titulares locales |
| `home_top_attacker_pct` | Percentil del mejor atacante titular local |
| `home_avg_def_percentile` | Percentil defensivo promedio de los defensas titulares |
| `home_avg_starter_percentile` | Percentil general promedio de todos los titulares |
| `home_gk_percentile` | Percentil del portero titular |
| `away_avg_att_percentile` | (ídem visitante) |
| … | … |

**Las 14 métricas por jugador usadas para calcular percentiles:**

| Métrica | Categoría |
|---|---|
| `goals` | Ofensivo |
| `assists` | Ofensivo |
| `shots_on_goal` | Ofensivo |
| `key_passes` | Creativo |
| `dribbles_success` | Individual |
| `rating` | Global |
| `tackles` | Defensivo |
| `interceptions` | Defensivo |
| `duels_won` | Físico/Duelo |
| `saves` | Portero |
| `goals_conceded` | Portero |
| `passes_accuracy` | Técnico |
| `minutes_played` | Participación |
| `yellow_cards` | Disciplina |

**Cómo usa el modelo:** los percentiles normalizan el rendimiento respecto a la liga, haciendo comparables partidos entre diferentes competiciones o niveles.

---

## 11. H2H Features (Enfrentamientos Directos)

**Archivo:** `feature_engineering.py`, función `compute_h2h_features()`  
**Ventana:** últimos 5 enfrentamientos directos entre estos dos equipos (`DEFAULT_H2H = 5`)  
**Incluye:** partidos donde ambos equipos se enfrentaron en cualquier dirección

| Feature | Descripción |
|---|---|
| `h2h_home_wins` | % de partidos en que ganó el equipo que juega de local hoy |
| `h2h_draws` | % de empates en el historial |
| `h2h_away_wins` | % de partidos en que ganó el equipo que juega de visitante hoy |
| `h2h_avg_goals` | Promedio de goles por partido en el historial |
| `h2h_count` | Número de enfrentamientos considerados |
| `h2h_home_avg_rating` | Rating promedio de titulares locales en el H2H |
| `h2h_away_avg_rating` | Rating promedio de titulares visitantes en el H2H |
| `h2h_home_goals_pstarted` | Goles por titular del equipo local en el H2H |
| `h2h_away_goals_pstarted` | Goles por titular del equipo visitante en el H2H |

**Cómo usa el modelo:** capturan dinámicas específicas entre estos dos equipos que no se reflejan en el rendimiento general (ej. un equipo que históricamente domina a su rival aunque ambos tengan stats similares).

---

## 12. League-Level Features (Estadísticas de Liga)

**Archivo:** `feature_engineering.py`, función `compute_league_rates()`  
**Cálculo:** expanding mean con shift, filtrado por `league_id`  
**Mínimo de partidos requeridos:** 10 (si hay menos, se asigna NaN)

| Feature | Descripción |
|---|---|
| `league_home_win_rate` | % históricas victorias locales en esta liga |
| `league_draw_rate` | % históricas de empates en esta liga |
| `league_away_win_rate` | % históricas victorias visitantes en esta liga |
| `league_avg_goals` | Promedio goles por partido en esta liga |
| `league_over25_rate` | % partidos con Over 2.5 en esta liga |
| `league_over15_rate` | % partidos con Over 1.5 en esta liga |
| `league_btts_rate` | % partidos con BTTS en esta liga |

**Cómo usa el modelo:** establecen el **contexto de la competición**. Una liga con `league_draw_rate = 0.35` es más propensa a empates que una con 0.20. El modelo ajusta su predicción respecto a la normalidad de esa liga.

---

## 13. Rest & Match Info

| Feature | Descripción |
|---|---|
| `home_days_rest` | Días desde el último partido del equipo local |
| `away_days_rest` | Días desde el último partido del equipo visitante |
| `league_id` | Identificador de la liga (variable categórica) |
| `season` | Temporada del partido |

**Cómo usa el modelo:** `days_rest` captura efectos de fatiga física. Un equipo que jugó hace 2 días puede tener desventaja frente a uno que descansó 7 días.

---

## 14. Ponderaciones y Calibración del Modelo

### 14.1 Class Weights (Pesos de Clase)

El modelo 1x2 usa **pesos de clase** para compensar el desequilibrio:

| Clase | Peso | Motivo |
|---|---|---|
| 0 (Victoria Local) | 1.00 | Clase más frecuente |
| 1 (Empate) | **1.22** | Clase minoritaria; subida desde 1.12 en v3 |
| 2 (Victoria Visitante) | 1.00 | Frecuencia intermedia |

Para los modelos binarios (Over/Under, BTTS):
```
scale_pos_weight = sqrt(n_negativos / n_positivos)
```
Usa raíz cuadrada para evitar sobreajuste hacia la clase positiva.

### 14.2 Hiperparámetros LightGBM

**Modelo 1x2 (`LGBM_BASE`):**

| Parámetro | Valor | Efecto |
|---|---|---|
| `n_estimators` | 2500 | Número de árboles |
| `learning_rate` | 0.015 | Tasa de aprendizaje baja = más generalizable |
| `num_leaves` | 18 | Árboles poco profundos = menos overfitting |
| `min_child_samples` | 85 | Mínimo de samples por hoja = regularización |
| `subsample` | 0.72 | 72% de filas por árbol = variabilidad |
| `colsample_bytree` | 0.48 | 48% de features por árbol = variabilidad |
| `reg_alpha` | 1.2 | Regularización L1 (sparsity) |
| `reg_lambda` | 2.5 | Regularización L2 (estabilidad) |

**Modelos binarios (`LGBM_BINARY`):**

| Parámetro | Valor | vs Base |
|---|---|---|
| `num_leaves` | 12 | Menor (más regularizado) |
| `min_child_samples` | 120 | Mayor (más conservador) |
| `learning_rate` | 0.012 | Más lento |
| `reg_alpha` | 1.5 | Más L1 |
| `reg_lambda` | 3.0 | Más L2 |

### 14.3 Temperature Scaling (Calibración Post-Training)

Después del entrenamiento, las probabilidades se calibran con temperatura τ:

```
p_calibrada = softmax(logits / τ)
```

- τ < 1 → predicciones más extremas (más confiadas)
- τ > 1 → predicciones más suavizadas (menos confiadas)
- Rango de búsqueda: 0.85 a 2.30 (paso 0.05)
- Métrica de optimización: **RPS** (Ranked Probability Score) para 1x2, **log_loss** para binarios
- Se aplica en validación para evitar sobreajuste

### 14.4 Prior Correction

Corrección por desequilibrio de clases en predicción:

```
p_corregida = p_raw / class_weight
p_normalizada = p_corregida / sum(p_corregida)
```

Ajusta las probabilidades brutas para deshacer el sesgo introducido por los class weights durante el entrenamiento.

---

## 15. Protección de Features (Pinned Features por Modelo)

El selector de features elimina automáticamente features irrelevantes. Cada modelo tiene un conjunto de features **explícitamente protegidas** que nunca se eliminan porque son señales clave para su objetivo:

### `lgbm_1x2` — Draw-Pinned (14 features)
```
season_draw_rate          league_draw_rate
league_season_draw_rate   team_draw_vs_league
draw_tendency_index       draw_tendency_diff
both_draw_prone           draw_rate_diff_recent
both_teams_recent_draw_rate  match_balance_index
xg_balance                ppg_balance
h2h_draws                 both_teams_recent_draw_rate
```

### `lgbm_ou25`, `lgbm_over05`, `lgbm_over15`, `lgbm_over35` — OU-Pinned
```
combined_xg               defensive_porosity
total_season_goal_rate    goal_threat_product
league_avg_goals          league_over25_rate
league_over15_rate        league_btts_rate
home_roll_xg_for_last5    away_roll_xg_for_last5
home_roll_xg_against_last5  away_roll_xg_against_last5
home_roll_ou25_last5      away_roll_ou25_last5
home_roll_ou15_last5      away_roll_ou15_last5
home_season_gfpg          away_season_gfpg
home_season_gapg          away_season_gapg
diff_xg                   diff_xga
```

### `lgbm_btts` — BTTS-Pinned
```
both_teams_score_rate     clean_sheet_clash
home_roll_scored_last5    away_roll_scored_last5
home_roll_clean_sheet_last5  away_roll_clean_sheet_last5
home_roll_btts_last5      away_roll_btts_last5
league_btts_rate
home_roll_xg_for_last5    away_roll_xg_against_last5
home_roll_xg_against_last5  away_roll_xg_for_last5
home_season_gfpg          away_season_gfpg
```

### `lgbm_corners_lambda` — Corners-Pinned
```
combined_corners
home_roll_corner_kicks_last5    away_roll_corner_kicks_last5
home_roll_corners_against_last5 away_roll_corners_against_last5
home_roll_corner_ratio_last5    away_roll_corner_ratio_last5
diff_corners                    diff_corners_against
corner_dominance_diff
home_roll_possession_last5      away_roll_possession_last5
diff_possession
```

---

## 16. Pruning de Features (Pre-Selección)

Antes de entrenar, se eliminan automáticamente:

| Criterio | Umbral |
|---|---|
| Features con demasiados nulos | > 45% de valores NaN |
| Features constantes | Única modalidad (varianza = 0) |

---

## 17. Resumen por Objetivo de Predicción

| Feature Category | 1x2 | OU 2.5 | BTTS | Over 0.5/1.5/3.5 | Córners λ |
|---|---|---|---|---|---|
| Rolling generales | Alta | Media | Media | Media | Baja |
| Rolling por localía | Alta | Baja | Baja | Baja | Baja |
| Rolling btts/ou25/ou15 | N/A | **Crítica** | **Crítica** | **Crítica** | N/A |
| Rolling corner_kicks | Baja | Baja | N/A | N/A | **Crítica** |
| EMA | Alta | Media | Media | Media | Baja |
| Season form | Alta | Media | Baja | Media | Baja |
| Draw features | **Críticas** | N/A | N/A | N/A | N/A |
| Goals features (combined_xg, etc.) | Media | **Críticas** | **Críticas** | **Críticas** | Baja |
| Corners features (combined_corners, etc.) | Baja | N/A | N/A | N/A | **Críticas** |
| Balance features | Alta (empate) | Media | Media | Media | Baja |
| Diferenciales | Alta | Media | Media | Media | Media |
| Player rolling | Alta | Media | Media | Media | Baja |
| Percentiles | Media | Baja | Baja | Baja | N/A |
| H2H | Media | Media | Media | Media | Baja |
| Liga | Media | Alta | Alta | Alta | Baja |
| Rest | Baja | Baja | Baja | Baja | Baja |

---

## 18. Métricas de Evaluación

| Métrica | Uso |
|---|---|
| **RPS** (Ranked Probability Score) | Métrica principal para 1x2 — penaliza predicciones alejadas del resultado real |
| **Log Loss** | Métrica principal para binarios (OU25, BTTS) |
| **Accuracy** | Métrica secundaria interpretable |
| **Brier Score** | Evaluación de calibración de probabilidades |

El objetivo del modelo es minimizar el RPS, no maximizar accuracy, porque se busca que las probabilidades estén bien calibradas (no solo predecir la clase correcta).

---

*Generado desde: `data-api/ml/feature_engineering.py`, `train_model.py`, `predict_api.py`*  
*Versión del modelo: v4 — config `v4_multi_market` — 7 modelos (1x2 + OU 0.5/1.5/2.5/3.5 + BTTS + Córners λ)*
