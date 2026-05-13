package com.playerdatatracking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.Fixture;
import com.playerdatatracking.entities.indexaldata.Torneo;
import com.playerdatatracking.exceptions.db.PlayerDataDBException;
import com.playerdatatracking.exceptions.operations.PlayerInputException;
import com.playerdatatracking.operations.IndelxalData.fixture.GetLiveFixtures;
import com.playerdatatracking.operations.IndelxalData.indexal.GetStudiedLeagues;
import com.playerdatatracking.operations.IndelxalData.indexal.SearchFixtures;
import com.playerdatatracking.responses.GenericResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class FixtureControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private GetLiveFixtures operationGetLiveFixtures;
    @Mock
    private GetStudiedLeagues operationGetStudiedLeagues;
    @Mock
    private SearchFixtures operationSearchFixtures;

    @InjectMocks
    private FixtureController fixtureController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(fixtureController).build();
    }

    // --- /liveFixtures ---

    @Test
    void liveFixtures_returnsOkWithFixtureList() throws Exception {
        GenericResponse<Fixture> op = okResponse(List.of(fixture("Real Madrid", "Barcelona")));
        when(operationGetLiveFixtures.ejecutar()).thenReturn(op);

        mockMvc.perform(post("/liveFixtures").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entityList").isArray())
                .andExpect(jsonPath("$.entityList[0].homeTeamName").value("Real Madrid"));
    }

    @Test
    void liveFixtures_emptyList_returnsOk() throws Exception {
        when(operationGetLiveFixtures.ejecutar()).thenReturn(okResponse(List.of()));

        mockMvc.perform(post("/liveFixtures").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void liveFixtures_dbException_returnsErrorCode() throws Exception {
        when(operationGetLiveFixtures.ejecutar()).thenThrow(new PlayerDataDBException("DB error"));

        mockMvc.perform(post("/liveFixtures").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Constants.CODE_ERR_PDDB))
                .andExpect(jsonPath("$.description").value(org.hamcrest.Matchers.containsString("DB error")));
    }

    // --- /studiedLeagues ---

    @Test
    void studiedLeagues_returnsLeagueList() throws Exception {
        GenericResponse<Torneo> op = new GenericResponse<>();
        op.setCODE(Constants.CODE_OK);
        op.setDescription("OK");
        op.setEntityList(List.of(torneo(140L, "La Liga"), torneo(39L, "Premier League")));
        when(operationGetStudiedLeagues.ejecutar()).thenReturn(op);

        mockMvc.perform(post("/studiedLeagues").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entityList").isArray())
                .andExpect(jsonPath("$.entityList[0].name").value("La Liga"))
                .andExpect(jsonPath("$.entityList[1].name").value("Premier League"));
    }

    @Test
    void studiedLeagues_dbException_returnsErrorCode() throws Exception {
        when(operationGetStudiedLeagues.ejecutar()).thenThrow(new PlayerDataDBException("connection lost"));

        mockMvc.perform(post("/studiedLeagues").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Constants.CODE_ERR_PDDB));
    }

    // --- /searchFixtures ---

    @Test
    void searchFixtures_byTeam_returnsResults() throws Exception {
        when(operationSearchFixtures.ejecutar(any())).thenReturn(okResponse(List.of(fixture("Arsenal", "Chelsea"))));

        mockMvc.perform(post("/searchFixtures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Arsenal\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entityList[0].homeTeamName").value("Arsenal"));
    }

    @Test
    void searchFixtures_playerInputException_returnsInputErrorCode() throws Exception {
        when(operationSearchFixtures.ejecutar(any())).thenThrow(new PlayerInputException("missing params"));

        mockMvc.perform(post("/searchFixtures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Constants.CODE_ERR_INPUT_EXCEPTION));
    }

    @Test
    void searchFixtures_dbException_returnsDbErrorCode() throws Exception {
        when(operationSearchFixtures.ejecutar(any())).thenThrow(new PlayerDataDBException("DB down"));

        mockMvc.perform(post("/searchFixtures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Liverpool\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Constants.CODE_ERR_PDDB));
    }

    // --- helpers ---

    private GenericResponse<Fixture> okResponse(List<Fixture> list) {
        GenericResponse<Fixture> r = new GenericResponse<>();
        r.setCODE(Constants.CODE_OK);
        r.setDescription("OK");
        r.setEntityList(list);
        return r;
    }

    private Fixture fixture(String home, String away) {
        Fixture f = new Fixture();
        f.setHomeTeamName(home);
        f.setAwayTeamName(away);
        f.setStatusShort("FT");
        return f;
    }

    private Torneo torneo(Long id, String name) {
        Torneo t = new Torneo();
        t.setId(id);
        t.setName(name);
        t.setStudied(true);
        return t;
    }
}
