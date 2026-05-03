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

## Architecture

### Backend (`backend/src/main/java/com/playerdatatracking/`)

The backend is Spring Boot 3.3 + Java 17. Key structural layers:

- **`controller/`** — `LoginController`, `MainController` are the only REST entry points. All requests are routed through `MainController` using `GenericRequest`/`GenericResponse` DTOs.
- **`operations/IndelxalData/`** — Business logic units (one class per operation: `IngestRawData`, `GetIndexedPlayer`, `TransferCheckOfPlayers`, `UpdatePlayersBySquads`, etc.). Controllers delegate to these.
- **`services/`** — Cross-cutting services. `PlayerJsonIngestService` handles bulk JSON import with parallel processing. `UserService` handles auth.
- **`clients/`** — HTTP clients for external APIs: `ApiFootballClient` and `PlayerDataClient`.
- **`entities/indexaldata/`** — 21 JPA entities mapping to PostgreSQL (Player, Club, League, Fixture, FixtureEvent, FixturePlayerStats, FixtureTeamStats, ManualTrackedPlayer, ConvertedPlayer, DuppedPlayers, PLAYER_QUALITIES, ConfigParams, Pais, ClubInLeague, etc.).
- **`repositories/`** — Spring Data JPA repos split into `indexaldata/`, `user/`, and `keys/` sub-packages.
- **`common/`** — `Constants.java` and `Methods.java` hold shared logic. `crypto/` contains RSA/AES utilities (`CryptoRSAService`, `AESCrypto`, `RsaKeyProvider`).
- **`configuration/`** — `SecurityConfig`, `CorsConfig`, `SessionUserAuthFilter`. Sessions are Redis-backed (Spring Session Data Redis, 30m timeout).
- **`exceptions/`** — Custom exceptions organized by domain (`apikeys/`, `db/`, `file/`, `operations/`).

### Infrastructure

- **Database:** PostgreSQL on `localhost:5432`
- **Session store:** Redis on `localhost:6379`
- **File uploads:** Max 6 MB; Excel files processed via Apache POI
- **Auth:** RSA-encrypted session tokens, filtered by `SessionUserAuthFilter`
- **`application.properties` is git-ignored** — developers must create their own locally

### Frontend (`frontend/.../src/app/`)

Angular 16 SPA. Feature modules: `add-player/`, `index-player/`, `manage-apikeys/`, `manage-indexal-db/`. Shared layout in `header/`, `footer/`, `home/`. Core guards/services in `core/`. Note: the `entitites/` directory has a typo (double-t) — keep it consistent if adding files there.

### Data API (`data-api/`)

A separate, experimental API service tracked in its own branch (`develop-api-data`). Has its own README and TO_DO.
