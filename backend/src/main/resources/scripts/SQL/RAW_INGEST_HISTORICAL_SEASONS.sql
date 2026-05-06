-- ──────────────────────────────────────────────────────────────────────────
-- raw_ingest_2023 / raw_ingest_2024
--
-- Tablas espejo de raw_ingest para almacenar los JSONs ingeridos de las
-- temporadas 2023 y 2024 por separado. La tabla raw_ingest sigue siendo la
-- destinataria de la temporada actual (ACTUAL_SEASON en config_params).
--
-- La columna season se mantiene aunque sea redundante con el nombre de la
-- tabla, para que el resto de código siga funcionando sin saber a qué
-- tabla apunta y conservar idempotencia por (payload_sha1).
-- ──────────────────────────────────────────────────────────────────────────

-- raw_ingest_2023
CREATE TABLE IF NOT EXISTS public.raw_ingest_2023 (
    id            BIGSERIAL    PRIMARY KEY,
    source        VARCHAR(50)  NOT NULL,
    team_id       INTEGER          NULL,
    season        VARCHAR(10)      NULL,
    filename      VARCHAR(255) NOT NULL,
    payload_sha1  VARCHAR(40)  NOT NULL,
    payload       JSONB        NOT NULL,
    ingested_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT raw_ingest_2023_payload_sha1_key UNIQUE (payload_sha1)
);

CREATE INDEX IF NOT EXISTS idx_raw_ingest_2023_team_season
    ON public.raw_ingest_2023 (team_id, season);

CREATE INDEX IF NOT EXISTS idx_raw_ingest_2023_ingested_at
    ON public.raw_ingest_2023 (ingested_at DESC);


-- raw_ingest_2024
CREATE TABLE IF NOT EXISTS public.raw_ingest_2024 (
    id            BIGSERIAL    PRIMARY KEY,
    source        VARCHAR(50)  NOT NULL,
    team_id       INTEGER          NULL,
    season        VARCHAR(10)      NULL,
    filename      VARCHAR(255) NOT NULL,
    payload_sha1  VARCHAR(40)  NOT NULL,
    payload       JSONB        NOT NULL,
    ingested_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT raw_ingest_2024_payload_sha1_key UNIQUE (payload_sha1)
);

CREATE INDEX IF NOT EXISTS idx_raw_ingest_2024_team_season
    ON public.raw_ingest_2024 (team_id, season);

CREATE INDEX IF NOT EXISTS idx_raw_ingest_2024_ingested_at
    ON public.raw_ingest_2024 (ingested_at DESC);
