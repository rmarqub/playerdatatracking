# PLAYERDATATRACKING — Data API

Servicios Python (FastAPI + uvicorn) que complementan el backend Spring Boot con capacidades de ML y valoración.

## Servicios

### `predict_api.py` — Puerto 8001

Predicción de resultados de partidos mediante modelos LightGBM entrenados con datos históricos.

**Endpoints principales:**

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/health` | Estado del servicio y modelos cargados |
| `POST` | `/predict` | Predicción de un partido (`{"fixture_id": 123}`) |
| `POST` | `/compute-player-percentiles` | Recalcula percentiles por temporada |
| `POST` | `/refresh-percentiles` | Refresca la caché de percentiles |

**Modelos disponibles:** 1x2, Over/Under (0.5 / 1.5 / 2.5 / 3.5), BTTS, Córners (Poisson).

---

### `valuation_api.py` — Puerto 8002

Valoración de mercado de jugadores en el rango **0–200.000.000 €** usando una fórmula paramétrica basada en percentiles, edad, tier de liga y participación.

**Endpoints:**

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/health` | Estado del servicio |
| `GET` | `/player-value/{index_id}` | Valor estimado + factores detallados |

**Fórmula:**
```
valor = 200M × (perf_score/100)² × age_factor × tier_factor × minutes_factor × injury_penalty
```

**Factores:**

| Factor | Fuente |
|---|---|
| `perf_score` | Percentiles ponderados por posición (tabla `player_season_percentiles`) |
| `age_factor` | Curva asimétrica; pico 24–27 (×1.00), mínimo 36+ (×0.18) |
| `tier_factor` | Tabla `league_tier`; Tier 1 élite ×1.00, Tier 5 menor ×0.18 |
| `minutes_factor` | Participación relativa al máximo de minutos posibles |
| `injury_penalty` | ×0.80 si el jugador está lesionado |

---

## Arranque

```bash
# Instalar dependencias (una sola vez)
pip install -r data-api/ml/requirements.txt

# API de predicción
python -m uvicorn predict_api:app --host 127.0.0.1 --port 8001

# API de valoración
python -m uvicorn valuation_api:app --host 127.0.0.1 --port 8002
```

En producción ambas APIs se lanzan automáticamente mediante `start.bat` o por `PythonApiLauncher.java` al iniciar Spring Boot (con health-check para no duplicar procesos).

Los logs en ejecución se guardan en `data-api/ml/logs/`.

## Variables de entorno

Copiar `data-api/ml/.env.example` como `data-api/ml/.env` y ajustar:

```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=playerdata
DB_USER=postgres
DB_PASSWORD=tu_password
```
