-- Tabla de percentiles de jugadores calculados desde player_match_stats.
-- league_id = 0 → percentil global (todos los jugadores de la misma temporada)
-- league_id = N → percentil de liga (jugadores de esa liga+temporada)

CREATE TABLE IF NOT EXISTS player_percentiles (
    id                    BIGSERIAL   PRIMARY KEY,
    player_id             BIGINT      NOT NULL,
    index_id              BIGINT,
    league_id             INTEGER     NOT NULL DEFAULT 0,
    season                TEXT        NOT NULL,
    pct_minutes           SMALLINT,
    pct_rating            SMALLINT,
    pct_goals_p90         SMALLINT,
    pct_assists_p90       SMALLINT,
    pct_shots_total_p90   SMALLINT,
    pct_shots_on_p90      SMALLINT,
    pct_passes_total_p90  SMALLINT,
    pct_passes_key_p90    SMALLINT,
    pct_pass_accuracy     SMALLINT,
    pct_tackles_p90       SMALLINT,
    pct_interceptions_p90 SMALLINT,
    pct_duels_won         SMALLINT,
    pct_dribbles_success  SMALLINT,
    pct_fouls_drawn_p90   SMALLINT,
    computed_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_player_percentiles UNIQUE (player_id, league_id, season)
);

CREATE INDEX IF NOT EXISTS idx_pp_index_id
    ON player_percentiles (index_id, season);

CREATE INDEX IF NOT EXISTS idx_pp_player_season
    ON player_percentiles (player_id, season);
