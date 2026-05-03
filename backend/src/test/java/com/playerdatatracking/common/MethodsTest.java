package com.playerdatatracking.common;

import com.playerdatatracking.exceptions.apikeys.ApiKeyManagementException;
import com.playerdatatracking.exceptions.db.PlayerDataDBException;
import com.playerdatatracking.exceptions.operations.MalformedRequestException;
import com.playerdatatracking.exceptions.operations.NoPlayerFoundException;
import com.playerdatatracking.exceptions.operations.PlayerInputException;
import com.playerdatatracking.requests.GenericRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class MethodsTest {

    // ── exceptionCodeManagement ─────────────────────────────────────────────

    @Test
    void exceptionCodeManagement_null_returnsMinusOne() {
        assertThat(Methods.exceptionCodeManagement(null)).isEqualTo(-1);
    }

    @Test
    void exceptionCodeManagement_playerDataDBException_returnsDbCode() {
        assertThat(Methods.exceptionCodeManagement(new PlayerDataDBException("e")))
                .isEqualTo(Constants.CODE_ERR_PDDB);
    }

    @Test
    void exceptionCodeManagement_playerInputException_returnsInputCode() {
        assertThat(Methods.exceptionCodeManagement(new PlayerInputException("e")))
                .isEqualTo(Constants.CODE_ERR_INPUT_EXCEPTION);
    }

    @Test
    void exceptionCodeManagement_malformedRequestException_returnsMalformedCode() {
        assertThat(Methods.exceptionCodeManagement(new MalformedRequestException("e")))
                .isEqualTo(Constants.CODE_ERR_MALFORMED_PARAMS);
    }

    @Test
    void exceptionCodeManagement_noPlayerFoundException_returnsNoPlayerCode() {
        assertThat(Methods.exceptionCodeManagement(new NoPlayerFoundException("e")))
                .isEqualTo(Constants.CODE_ERR_NO_PLAYER_FOUND);
    }

    @Test
    void exceptionCodeManagement_apiKeyManagementException_returnsKeyMngmtCode() {
        assertThat(Methods.exceptionCodeManagement(new ApiKeyManagementException("e")))
                .isEqualTo(Constants.CODE_ERR_KEY_MNGMT);
    }

    @Test
    void exceptionCodeManagement_unknownException_returnsMinusOne() {
        assertThat(Methods.exceptionCodeManagement(new RuntimeException("e")))
                .isEqualTo(-1);
    }

    @Test
    void exceptionCodeManagement_ioException_returnsXslCode() throws Exception {
        assertThat(Methods.exceptionCodeManagement(new java.io.IOException("e")))
                .isEqualTo(Constants.CODE_ERR_XSL_READING_EXCEPTION);
    }

    // ── parseLocalDateFlexible ───────────────────────────────────────────────

    @Test
    void parseLocalDate_isoFormat_parses() {
        assertThat(Methods.parseLocalDateFlexible("2024-03-15"))
                .isEqualTo(LocalDate.of(2024, 3, 15));
    }

    @Test
    void parseLocalDate_ddMMyyyySlash_parses() {
        assertThat(Methods.parseLocalDateFlexible("15/03/2024"))
                .isEqualTo(LocalDate.of(2024, 3, 15));
    }

    @Test
    void parseLocalDate_yyyyMMddSlash_parses() {
        assertThat(Methods.parseLocalDateFlexible("2024/03/15"))
                .isEqualTo(LocalDate.of(2024, 3, 15));
    }

    @Test
    void parseLocalDate_null_throwsIllegalArgument() {
        assertThatThrownBy(() -> Methods.parseLocalDateFlexible(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseLocalDate_blankString_throwsIllegalArgument() {
        assertThatThrownBy(() -> Methods.parseLocalDateFlexible("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseLocalDate_unsupportedFormat_throwsWithMessage() {
        assertThatThrownBy(() -> Methods.parseLocalDateFlexible("15-03-2024"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported date format");
    }

    // ── playerWrongValues ────────────────────────────────────────────────────

    @Test
    void playerWrongValues_allMissing_returnsNombreClubBirth() {
        GenericRequest req = new GenericRequest();
        List<String> errors = Methods.playerWrongValues(req);
        assertThat(errors).containsExactlyInAnyOrder("Nombre", "Club", "Birth");
    }

    @Test
    void playerWrongValues_validRequest_returnsEmpty() {
        GenericRequest req = new GenericRequest();
        req.setNombre("Messi");
        req.setClub("Inter Miami");
        req.setBirth("1987-06-24");
        assertThat(Methods.playerWrongValues(req)).isEmpty();
    }

    @Test
    void playerWrongValues_missingNombre_containsNombre() {
        GenericRequest req = new GenericRequest();
        req.setClub("Real Madrid");
        req.setBirth("1990-01-01");
        assertThat(Methods.playerWrongValues(req)).contains("Nombre");
    }

    @Test
    void playerWrongValues_missingClub_containsClub() {
        GenericRequest req = new GenericRequest();
        req.setNombre("Ronaldo");
        req.setBirth("1985-02-05");
        assertThat(Methods.playerWrongValues(req)).contains("Club");
    }

    @Test
    void playerWrongValues_emptyNombre_containsNombre() {
        GenericRequest req = new GenericRequest();
        req.setNombre("");
        req.setClub("Barcelona");
        req.setBirth("2000-01-01");
        assertThat(Methods.playerWrongValues(req)).contains("Nombre");
    }

    // ── bindRequestAsPlayer ──────────────────────────────────────────────────

    @Test
    void bindRequestAsPlayer_validRequest_setsNameAndClub() throws Exception {
        GenericRequest req = new GenericRequest();
        req.setNombre("Rodri");
        req.setClub("Man City");
        req.setBirth("1996-06-22");

        var player = Methods.bindRequestAsPlayer(req);

        assertThat(player.getNombre()).isEqualTo("Rodri");
        assertThat(player.getClub()).isEqualTo("Man City");
    }

    @Test
    void bindRequestAsPlayer_missingNombre_throwsMalformed() {
        GenericRequest req = new GenericRequest();
        req.setClub("Real Madrid");
        req.setBirth("1990-01-01");

        assertThatThrownBy(() -> Methods.bindRequestAsPlayer(req))
                .isInstanceOf(MalformedRequestException.class)
                .hasMessageContaining("Nombre");
    }

    @Test
    void bindRequestAsPlayer_missingClub_throwsMalformed() {
        GenericRequest req = new GenericRequest();
        req.setNombre("Player");
        req.setBirth("1990-01-01");

        assertThatThrownBy(() -> Methods.bindRequestAsPlayer(req))
                .isInstanceOf(MalformedRequestException.class)
                .hasMessageContaining("Club");
    }

    // ── getTournamentType ────────────────────────────────────────────────────

    @Test
    void getTournamentType_worldCup_returnsInternational() {
        assertThat(Methods.getTournamentType(Constants.MUNDIAL, Constants.WORLD, "Cup"))
                .isEqualTo(Constants.INTERNATIONAL_ID);
    }

    @Test
    void getTournamentType_nationalLeague_returnsLeagueId() {
        assertThat(Methods.getTournamentType("La Liga", "Spain", Constants.LEAGUE))
                .isEqualTo(Constants.LEAGUE_ID);
    }

    @Test
    void getTournamentType_nationalCup_returnsCupId() {
        assertThat(Methods.getTournamentType("Copa del Rey", "Spain", "Cup"))
                .isEqualTo(Constants.CUP_ID);
    }

    @Test
    void getTournamentType_unknownWorldTournament_returnsCopIntId() {
        assertThat(Methods.getTournamentType("Some Unknown Cup", Constants.WORLD, "Cup"))
                .isEqualTo(Constants.COPA_INT_ID);
    }

    @Test
    void getTournamentType_copaAmerica_returnsInternational() {
        assertThat(Methods.getTournamentType(Constants.COPA_AMERICA, Constants.WORLD, "Cup"))
                .isEqualTo(Constants.INTERNATIONAL_ID);
    }

    @Test
    void getTournamentType_euroChampionship_returnsInternational() {
        assertThat(Methods.getTournamentType(Constants.EURO, Constants.WORLD, "Cup"))
                .isEqualTo(Constants.INTERNATIONAL_ID);
    }
}
