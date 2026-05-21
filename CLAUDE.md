# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PlayerDataTracking is a football/soccer player data management system. It ingests match and player statistics from external APIs, stores them in PostgreSQL, and exposes them through a Spring Boot REST API consumed by an Angular frontend.

## Branch Strategy

- `main` — stable releases
- `develop` — integration branch
- `develop-backend`, `develop-frontend`, `develop-api-data` — feature branches per layer

## Commands

### Backend (Spring Boot / Maven)

```bash
# From backend/
mvn clean package           # Build JAR
mvn spring-boot:run         # Run locally
mvn test                    # Run all tests
mvn test -Dtest=ClassName   # Run a single test class
```

### Frontend (Angular)

```bash
# From frontend/playerdatatrackingfront/playerdatatrackerfront/
npm install
npm start          # Dev server (ng serve)
npm run build      # Production build
npm test           # Karma/Jasmine tests
```

### Data API (Python / FastAPI)

```bash
# From data-api/ml/
pip install -r requirements.txt

# API de predicción de partidos — puerto 8001
python -m uvicorn predict_api:app --host 127.0.0.1 --port 8001

# API de valoración de jugadores — puerto 8002
python -m uvicorn valuation_api:app --host 127.0.0.1 --port 8002
```

> En producción ambas APIs arrancan automáticamente vía `start.bat` o por `PythonApiLauncher`
> al iniciar Spring Boot (hace health-check y solo lanza si no están ya activas).

### Scripts de utilidad (raíz del proyecto)

| Script | Descripción |
|---|---|
| `start.bat` | Inicia backend + ambas APIs Python + abre el navegador |
| `stop.bat` | Detiene todos los procesos |
| `import-db.bat` | Importa el volcado SQL de la base de datos |

## Architecture

### Backend (`backend/src/main/java/com/playerdatatracking/`)

The backend is Spring Boot 3.3 + Java 17. Key structural layers:

- **`controller/`** — Cuatro controladores REST: `LoginController`, `MainController`, `DataSyncController`, `IndexedPlayerController`. Todos usan el par `GenericRequest`/`GenericResponse` como DTOs de entrada/salida.
- **`operations/IndelxalData/`** — Unidades de lógica de negocio (una clase por operación): `IngestRawData`, `GetIndexedPlayer`, `GetLeagueTiers`, `UpdateLeagueTiers`, `GetPlayerMarketValue`, `TransferCheckOfPlayers`, `UpdatePlayersBySquads`, etc. Los controladores delegan en ellas.
- **`services/`** — Servicios transversales. `PlayerJsonIngestService` gestiona importación masiva JSON en paralelo. `UserService` maneja autenticación.
- **`clients/`** — Clientes HTTP hacia APIs externas e internas: `ApiFootballClient`, `PlayerDataClient`, `PredictApiClient` (puerto 8001), `ValuationApiClient` (puerto 8002).
- **`entities/indexaldata/`** — Entidades JPA sobre PostgreSQL: `Player`, `Club`, `League`, `Fixture`, `FixtureEvent`, `FixturePlayerStats`, `FixtureTeamStats`, `ManualTrackedPlayer`, `ConvertedPlayer`, `DuppedPlayers`, `PLAYER_QUALITIES`, `ConfigParams`, `Pais`, `ClubInLeague`, `LeagueTier`, etc.
- **`repositories/`** — Repos Spring Data JPA en sub-paquetes `indexaldata/`, `user/` y `keys/`. Incluye `LeagueTierRepository`.
- **`common/`** — `Constants.java` y `Methods.java` con lógica compartida. `crypto/` contiene utilidades RSA/AES (`CryptoRSAService`, `AESCrypto`, `RsaKeyProvider`).
- **`configuration/`** — `SecurityConfig`, `CorsConfig`, `SessionUserAuthFilter`, `PythonApiLauncher` (auto-lanza las APIs Python al arrancar Spring Boot con health-check para evitar duplicados).
- **`exceptions/`** — Excepciones propias por dominio (`apikeys/`, `db/`, `file/`, `operations/`).

### Infrastructure

- **Database:** PostgreSQL on `localhost:5432`
- **Session store:** JDBC (Spring Session JDBC, timeout 30 min)
- **File uploads:** Max 6 MB; Excel files processed via Apache POI
- **Auth:** RSA-encrypted session tokens, filtered by `SessionUserAuthFilter`
- **`application.properties` is git-ignored** — copy `application.properties.template` and fill in your values

Propiedades clave en `application.properties`:

```properties
predict.api.url=http://localhost:8001    # URL de la API de predicción
valuation.api.url=http://localhost:8002  # URL de la API de valoración
# python.api.dir=                        # Override manual del directorio data-api/ml (opcional)
```

### Frontend (`frontend/.../src/app/`)

Angular 16 SPA. Feature modules: `add-player/`, `index-player/`, `manage-apikeys/`, `manage-indexal-db/`, `league-management/`, `player-detail/`. Shared layout in `header/`, `footer/`, `home/`. Core guards/services in `core/`. Note: the `entitites/` directory has a typo (double-t) — keep it consistent if adding files there.

### Data API (`data-api/ml/`)

Dos servicios FastAPI independientes en Python, arrancados automáticamente por `start.bat` y por `PythonApiLauncher` al iniciar Spring Boot.

| Archivo | Puerto | Descripción |
|---|---|---|
| `predict_api.py` | 8001 | Predicción de resultados de partidos (modelos LightGBM) |
| `valuation_api.py` | 8002 | Valoración de mercado de jugadores (fórmula paramétrica) |

Ambas exponen `GET /health` para el health-check del launcher.

#### Módulo de valoración de mercado (`valuation_api.py`)

Calcula el valor estimado de un jugador en el rango 0–200.000.000 €:

```
valor = 200M × (perf_score/100)² × age_factor × tier_factor × minutes_factor × injury_penalty
```

- **`GET /player-value/{index_id}`** — devuelve el valor y todos los factores intermedios.
- El rendimiento se calcula a partir de percentiles por posición dominante (G/D/M/F) almacenados en `player_season_percentiles`.
- El tier de liga se obtiene de la tabla `league_tier` (Tier 1 élite → factor 1.00, Tier 5 menor → factor 0.18).
- Los logs se escriben en `data-api/ml/logs/valuation_api.log`.

#### Tabla `league_tier`

Gestiona el tier de cada competición (0–5). Se administra desde el panel de ligas del frontend (`/league-management`). SQL de creación en `backend/src/main/resources/scripts/SQL/create_league_tier.sql`.
