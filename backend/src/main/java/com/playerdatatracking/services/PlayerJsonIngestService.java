package com.playerdatatracking.services;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Service
public class PlayerJsonIngestService {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper om = new ObjectMapper();

    public PlayerJsonIngestService(DataSource ds, TransactionTemplate tx) {
        this.jdbc = new JdbcTemplate(ds);
        this.tx = tx;
    }

    public void ingestAllPlayers(String rootDir, boolean purgeBeforeRun, int parallelism) throws Exception {
        final boolean rebuildMode = purgeBeforeRun;

        if (purgeBeforeRun) {
            tx.execute(s -> { jdbc.update("TRUNCATE TABLE player_match_stats"); return null; });
        }

        List<Path> files = listAllJsonFiles(Paths.get(rootDir));
        if (files.isEmpty()) return;

        //1: RAWs en paralelo
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, parallelism));
        ConcurrentLinkedQueue<Long> newRawIds = new ConcurrentLinkedQueue<>();

        List<Future<?>> futures = new ArrayList<>(files.size());
        for (Path p : files) {
            futures.add(pool.submit(() -> {
                try {
                    Long rawId = processRawOnly(p);
                    if (rawId != null) newRawIds.add(rawId);
                } catch (Exception e) {
                    Throwable t = e; while (t.getCause()!=null) t=t.getCause();
                    System.err.println("Error procesando RAW " + p + ": " + t.getMessage());
                }
            }));
        }
        for (Future<?> f : futures) f.get();
        pool.shutdown();

        // 2: TRANSFORM
        if (rebuildMode) {
            // reconstruye desde TODOS los raw existentes
            List<Long> allRawIds = jdbc.query(
                "SELECT id FROM raw_ingest ORDER BY id",
                (rs, rn) -> rs.getLong(1)
            );
            for (Long rid : allRawIds) {
                transformOneRawWithRetry(rid);
            }
        } else {
            // solo los nuevos en esta ejecución
            for (Long rid : newRawIds) {
                transformOneRawWithRetry(rid);
            }
        }
    }


    private List<Path> listAllJsonFiles(Path root) throws IOException {
        List<Path> result = new ArrayList<>();
        if (!Files.exists(root)) return result;
        try (DirectoryStream<Path> teams = Files.newDirectoryStream(root)) {
            for (Path teamDir : teams) {
                if (!Files.isDirectory(teamDir)) continue;
                try (DirectoryStream<Path> jsons = Files.newDirectoryStream(teamDir)) {
                    for (Path f : jsons) {
                        if (Files.isRegularFile(f) && f.toString().toLowerCase().endsWith(".json")) {
                            result.add(f);
                        }
                    }
                }
            }
        }
        return result;
    }

    private void processOneFile(Path file) throws Exception {
        // Lee fichero
        String jsonText = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim();
        if (jsonText.isEmpty()) return;

        // Calcula SHA1 (idempotencia)
        String sha1 = sha1Hex(jsonText);

        // Extrae metadatos básicos (team_id, season) del JSON cuando existan
        Integer teamId = null;
        String season = null;
        try {
            JsonNode root = om.readTree(jsonText);
            JsonNode response0 = root.path("response").isArray() && root.path("response").size() > 0
                    ? root.path("response").get(0) : null;
            if (response0 != null) {
                JsonNode stats0 = response0.path("statistics").isArray() && response0.path("statistics").size() > 0
                        ? response0.path("statistics").get(0) : null;
                if (stats0 != null) {
                    if (stats0.path("team").hasNonNull("id"))
                        teamId = safeInt(stats0.path("team").path("id"));
                    if (stats0.path("league").hasNonNull("season"))
                        season = stats0.path("league").path("season").asText(null);
                }
            }
        } catch (Exception ignore) {
            // Si no podemos inferir, lo dejamos null; no es bloqueante
        }

        final Integer teamIdF = teamId;
        final String seasonF = season;
        final String filename = file.getFileName().toString();

        // Transacción por fichero: inserta RAW (ON CONFLICT DO NOTHING) y transforma a destino
        tx.execute(status -> {
            // 1) RAW: inserta si no existe el mismo SHA1
            Long rawId = insertRawIfNew("api-sports", teamIdF, seasonF, filename, sha1, jsonText);

            // 2) TRANSFORM: si rawId != null, volcamos ese registro a player_match_stats
            if (rawId != null) {
                upsertPlayerMatchStatsFromRaw(rawId);
            }
            return null;
        });
    }

    private Long insertRawIfNew(String source, Integer teamId, String season, String filename, String sha1, String payload) {
        // Usamos RETURNING id para obtener el id insertado. Si ON CONFLICT DO NOTHING, no devuelve fila.
    	String sql = ""
    			  + "INSERT INTO raw_ingest (source, team_id, season, filename, payload_sha1, payload) "
    			  + "VALUES (?, ?, ?, ?, ?, ?::jsonb) "
    			  + "ON CONFLICT (payload_sha1) DO NOTHING "
    			  + "RETURNING id";

        List<Long> ids = jdbc.query(sql,
                ps -> {
                    ps.setString(1, source);
                    if (teamId == null) ps.setNull(2, java.sql.Types.INTEGER); else ps.setInt(2, teamId);
                    if (season == null) ps.setNull(3, java.sql.Types.VARCHAR); else ps.setString(3, season);
                    ps.setString(4, filename);
                    ps.setString(5, sha1);
                    ps.setString(6, payload);
                },
                (rs, rowNum) -> rs.getLong(1));

        // Si no insertó (duplicado exacto), ids estará vacío; en ese caso no hace falta transformar de nuevo.
        return ids.isEmpty() ? null : ids.get(0);
    }

    private void upsertPlayerMatchStatsFromRaw(Long rawId) {
        final String sql = """
        		WITH src AS (
        				  SELECT payload FROM raw_ingest WHERE id = ?
        				),
        				players AS (
        				  SELECT jsonb_array_elements(payload->'response') AS item FROM src
        				),
        				exploded AS (
        				  SELECT
        				    item->'player'->>'id'   AS player_id,
        				    item->'player'->>'name' AS player_name,
        				    stats->'team'->>'id'    AS team_id,
        				    stats->'team'->>'name'  AS team_name,
        				    stats->'league'->>'id'  AS league_id_txt,
        				    stats->'league'->>'name' AS league_name,
        				    stats->'league'->>'season' AS season,
        				    stats->'games'->>'position' AS position,
        				    stats->'games'->>'rating'   AS rating_raw,

        				    CAST(NULLIF(regexp_replace(stats->'games'->>'minutes', '[^0-9-]', '', 'g'), '') AS int) AS minutes,
        				    CAST(NULLIF(regexp_replace(stats->'shots'->>'total',  '[^0-9-]', '', 'g'), '') AS int) AS shots_total,
        				    CAST(NULLIF(regexp_replace(stats->'shots'->>'on',     '[^0-9-]', '', 'g'), '') AS int) AS shots_on,

        				    COALESCE(CAST(NULLIF(regexp_replace(stats->'goals'->>'total',   '[^0-9-]', '', 'g'), '') AS int), 0) AS goals,
        				    COALESCE(CAST(NULLIF(regexp_replace(stats->'goals'->>'assists', '[^0-9-]', '', 'g'), '') AS int), 0) AS assists,

        				    CAST(NULLIF(regexp_replace(stats->'passes'->>'total',    '[^0-9-]', '', 'g'), '') AS int) AS passes_total,
        				    CAST(NULLIF(regexp_replace(stats->'passes'->>'key',      '[^0-9-]', '', 'g'), '') AS int) AS passes_key,
        				    CAST(NULLIF(regexp_replace(stats->'passes'->>'accuracy', '[^0-9-]', '', 'g'), '') AS int) AS passes_acc,

        				    CAST(NULLIF(regexp_replace(stats->'tackles'->>'total',         '[^0-9-]', '', 'g'), '') AS int) AS tackles_total,
        				    CAST(NULLIF(regexp_replace(stats->'tackles'->>'interceptions', '[^0-9-]', '', 'g'), '') AS int) AS interceptions,

        				    CAST(NULLIF(regexp_replace(stats->'duels'->>'total', '[^0-9-]', '', 'g'), '') AS int) AS duels_total,
        				    CAST(NULLIF(regexp_replace(stats->'duels'->>'won',   '[^0-9-]', '', 'g'), '') AS int) AS duels_won,

        				    CAST(NULLIF(regexp_replace(stats->'dribbles'->>'attempts', '[^0-9-]', '', 'g'), '') AS int) AS dribbles_att,
        				    CAST(NULLIF(regexp_replace(stats->'dribbles'->>'success',  '[^0-9-]', '', 'g'), '') AS int) AS dribbles_suc,

        				    CAST(NULLIF(regexp_replace(stats->'fouls'->>'drawn',     '[^0-9-]', '', 'g'), '') AS int) AS fouls_drawn,
        				    CAST(NULLIF(regexp_replace(stats->'fouls'->>'committed', '[^0-9-]', '', 'g'), '') AS int) AS fouls_comm,
        				    CAST(NULLIF(regexp_replace(stats->'cards'->>'yellow',    '[^0-9-]', '', 'g'), '') AS int) AS yc,
        				    CAST(NULLIF(regexp_replace(stats->'cards'->>'red',       '[^0-9-]', '', 'g'), '') AS int) AS rc
        				  FROM players p
        				  CROSS JOIN LATERAL jsonb_array_elements(p.item->'statistics') AS stats
        				  WHERE (stats->'league'->>'id') ~ '^[0-9]+$'
        				),
        				ranked AS (
        				  SELECT
        				    *,
        				    ROW_NUMBER() OVER (
        				      PARTITION BY player_id, league_id_txt, season
        				      ORDER BY minutes DESC NULLS LAST, goals DESC NULLS LAST, passes_total DESC NULLS LAST
        				    ) AS rn
        				  FROM exploded
        				)
        				INSERT INTO player_match_stats (
        				  player_id, player_name, team_id, team_name,
        				  league_id, league_name, season, match_bucket,
        				  minutes, position, rating,
        				  shots_total, shots_on, goals, assists,
        				  passes_total, passes_key, passes_acc,
        				  tackles_total, interceptions, duels_total, duels_won,
        				  dribbles_att, dribbles_suc, fouls_drawn, fouls_comm, yc, rc
        				)
        				SELECT
        				  player_id::bigint, player_name,
        				  team_id::int, team_name,
        				  (league_id_txt)::int AS league_id,
        				  league_name, season,
        				  'league:' || (league_id_txt) AS match_bucket,          -- SIGUE igual
        				  minutes, position,
        				  CASE WHEN rating_raw ~ '^[0-9]+(\\.[0-9]+)?$' THEN rating_raw::numeric ELSE NULL END,
        				  shots_total, shots_on, goals, assists,
        				  passes_total, passes_key, passes_acc,
        				  tackles_total, interceptions, duels_total, duels_won,
        				  dribbles_att, dribbles_suc, fouls_drawn, fouls_comm, yc, rc
        				FROM ranked
        				WHERE rn = 1
        				ON CONFLICT (player_id, league_id, season, match_bucket) DO UPDATE SET
        				  minutes       = EXCLUDED.minutes,
        				  position      = EXCLUDED.position,
        				  rating        = EXCLUDED.rating,
        				  shots_total   = EXCLUDED.shots_total,
        				  shots_on      = EXCLUDED.shots_on,
        				  goals         = EXCLUDED.goals,
        				  assists       = EXCLUDED.assists,
        				  passes_total  = EXCLUDED.passes_total,
        				  passes_key    = EXCLUDED.passes_key,
        				  passes_acc    = EXCLUDED.passes_acc,
        				  tackles_total = EXCLUDED.tackles_total,
        				  interceptions = EXCLUDED.interceptions,
        				  duels_total   = EXCLUDED.duels_total,
        				  duels_won     = EXCLUDED.duels_won,
        				  dribbles_att  = EXCLUDED.dribbles_att,
        				  dribbles_suc  = EXCLUDED.dribbles_suc,
        				  fouls_drawn   = EXCLUDED.fouls_drawn,
        				  fouls_comm    = EXCLUDED.fouls_comm,
        				  yc            = EXCLUDED.yc,
        				  rc            = EXCLUDED.rc;
        		""";
        try {
        	jdbc.update(sql, rawId);
        } catch (Exception e) {
        	Throwable t = e;
        	while (t.getCause() != null) t = t.getCause();
        	System.err.println("Error procesando: " + t.getMessage());
        }
        
    }

    private static Integer safeInt(JsonNode n) {
        if (n == null || n.isNull()) return null;
        if (n.isInt()) return n.asInt();
        if (n.isTextual()) {
            try { return Integer.parseInt(n.asText()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static String sha1Hex(String text) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
    
    private void processOneFile(Path file, boolean rebuildMode) throws Exception {
        String jsonText = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim();
        if (jsonText.isEmpty()) return;

        String sha1 = sha1Hex(jsonText);

        Integer teamId = null;
        String season = null;
        try {
            JsonNode root = om.readTree(jsonText);
            JsonNode response0 = root.path("response").isArray() && root.path("response").size() > 0
                    ? root.path("response").get(0) : null;
            if (response0 != null) {
                JsonNode stats0 = response0.path("statistics").isArray() && response0.path("statistics").size() > 0
                        ? response0.path("statistics").get(0) : null;
                if (stats0 != null) {
                    if (stats0.path("team").hasNonNull("id"))
                        teamId = safeInt(stats0.path("team").path("id"));
                    if (stats0.path("league").hasNonNull("season"))
                        season = stats0.path("league").path("season").asText(null);
                }
            }
        } catch (Exception ignore) {}

        final Integer teamIdF = teamId;
        final String seasonF = season;
        final String filename = file.getFileName().toString();
        
        tx.execute(status -> {
            Long rawId = insertRawIfNew("api-sports", teamIdF, seasonF, filename, sha1, jsonText);

            if (rawId != null) {
                upsertPlayerMatchStatsFromRaw(rawId);
            } else if (rebuildMode) {
                Long existingId = jdbc.query(
                    "SELECT id FROM raw_ingest WHERE source=? AND filename=? AND payload_sha1=? " +
                    "AND (season IS NOT DISTINCT FROM ?) ORDER BY ingested_at DESC LIMIT 1",
                    ps -> {
                        ps.setString(1, "api-sports");
                        ps.setString(2, filename);
                        ps.setString(3, sha1);
                        if (seasonF == null) ps.setNull(4, java.sql.Types.VARCHAR); else ps.setString(4, seasonF);
                    },
                    (rs, rn) -> rs.getLong(1)
                ).stream().findFirst().orElse(null);

                if (existingId != null) {
                    upsertPlayerMatchStatsFromRaw(existingId);
                }
            }
            return null;
        });
    }
    
    private Long processRawOnly(Path file) throws Exception {
        String jsonText = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim();
        if (jsonText.isEmpty()) return null;

        // Evita RAW vacíos (response [])
        JsonNode root = om.readTree(jsonText);
        JsonNode resp = root.path("response");
        //if (!resp.isArray() || resp.size() == 0) return null;

        String sha1 = sha1Hex(jsonText);

        Integer teamId = null;
        String season = null;
        try {
            JsonNode response0 = resp.size() > 0 ? resp.get(0) : null;
            if (response0 != null) {
                JsonNode stats0 = response0.path("statistics");
                if (stats0.isArray() && stats0.size() > 0) {
                    JsonNode s0 = stats0.get(0);
                    if (s0.path("team").hasNonNull("id")) teamId = safeInt(s0.path("team").path("id"));
                    if (s0.path("league").hasNonNull("season")) season = s0.path("league").path("season").asText(null);
                }
            }
        } catch (Exception ignore) {}

        final Integer teamIdF = teamId;
        final String seasonF = season;
        final String filename = file.getFileName().toString();

        return tx.execute(status -> insertRawIfNew("api-sports", teamIdF, seasonF, filename, sha1, jsonText));
    }
    
    
    private void transformOneRawWithRetry(Long rawId) {
        final int MAX_RETRY = 3;
        int attempt = 0;
        while (true) {
            try {
                tx.execute(s -> { upsertPlayerMatchStatsFromRaw(rawId); return null; });
                return; //OK
            } catch (Exception e) {
                Throwable t = e;
                while (t.getCause() != null) t = t.getCause();
                String msg = t.getMessage() != null ? t.getMessage() : "";
                // SQLSTATE 40P01 (deadlock_detected)
                if (msg.contains("deadlock detected") || msg.contains("40P01")) {
                    if (++attempt <= MAX_RETRY) {
                        try { Thread.sleep(100L * attempt); } catch (InterruptedException ie) {}
                        continue; // reintenta
                    }
                }
                System.err.println("Error transformando RAW " + rawId + ": " + msg);
                return;
            }
        }
    }

}

