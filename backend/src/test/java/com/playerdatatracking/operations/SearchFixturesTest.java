package com.playerdatatracking.operations;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.Fixture;
import com.playerdatatracking.exceptions.db.PlayerDataDBException;
import com.playerdatatracking.exceptions.operations.PlayerInputException;
import com.playerdatatracking.operations.IndelxalData.SearchFixtures;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.GenericResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchFixturesTest {

    @Mock
    private PlayerDataClient pdClient;

    @InjectMocks
    private SearchFixtures searchFixtures;

    @Test
    void ejecutar_byTeamName_returnsList() throws Exception {
        GenericRequest request = new GenericRequest();
        request.setNombre("Real Madrid");
        List<Fixture> fixtures = List.of(fixture("Real Madrid", "Barcelona"));
        when(pdClient.searchFixturesByTeam("Real Madrid")).thenReturn(fixtures);

        GenericResponse<Fixture> response = searchFixtures.ejecutar(request);

        assertThat(response.getCODE()).isEqualTo(Constants.CODE_OK);
        assertThat(response.getEntityList()).hasSize(1);
        assertThat(response.getEntityList().get(0).getHomeTeamName()).isEqualTo("Real Madrid");
        verify(pdClient).searchFixturesByTeam("Real Madrid");
        verifyNoMoreInteractions(pdClient);
    }

    @Test
    void ejecutar_byLeagueId_returnsList() throws Exception {
        GenericRequest request = new GenericRequest();
        request.setId(140L);
        List<Fixture> fixtures = List.of(fixture("Atletico", "Sevilla"), fixture("Valencia", "Villarreal"));
        when(pdClient.searchFixturesByLeague(140)).thenReturn(fixtures);

        GenericResponse<Fixture> response = searchFixtures.ejecutar(request);

        assertThat(response.getCODE()).isEqualTo(Constants.CODE_OK);
        assertThat(response.getEntityList()).hasSize(2);
        verify(pdClient).searchFixturesByLeague(140);
        verifyNoMoreInteractions(pdClient);
    }

    @Test
    void ejecutar_noParams_throwsPlayerInputException() {
        GenericRequest request = new GenericRequest();

        assertThatThrownBy(() -> searchFixtures.ejecutar(request))
                .isInstanceOf(PlayerInputException.class);
        verifyNoInteractions(pdClient);
    }

    @Test
    void ejecutar_blankTeamName_throwsPlayerInputException() {
        GenericRequest request = new GenericRequest();
        request.setNombre("   ");

        assertThatThrownBy(() -> searchFixtures.ejecutar(request))
                .isInstanceOf(PlayerInputException.class);
        verifyNoInteractions(pdClient);
    }

    @Test
    void ejecutar_dbException_propagates() throws Exception {
        GenericRequest request = new GenericRequest();
        request.setNombre("Barcelona");
        when(pdClient.searchFixturesByTeam("Barcelona"))
                .thenThrow(new PlayerDataDBException("DB error"));

        assertThatThrownBy(() -> searchFixtures.ejecutar(request))
                .isInstanceOf(PlayerDataDBException.class)
                .hasMessageContaining("DB error");
    }

    @Test
    void ejecutar_emptyResults_returnsEmptyEntityList() throws Exception {
        GenericRequest request = new GenericRequest();
        request.setNombre("Unknown Team");
        when(pdClient.searchFixturesByTeam("Unknown Team")).thenReturn(List.of());

        GenericResponse<Fixture> response = searchFixtures.ejecutar(request);

        assertThat(response.getCODE()).isEqualTo(Constants.CODE_OK);
        assertThat(response.getEntityList()).isEmpty();
    }

    @Test
    void ejecutar_teamNameTrimmed_beforeSearch() throws Exception {
        GenericRequest request = new GenericRequest();
        request.setNombre("  Arsenal  ");
        when(pdClient.searchFixturesByTeam("Arsenal")).thenReturn(List.of());

        searchFixtures.ejecutar(request);

        verify(pdClient).searchFixturesByTeam("Arsenal");
    }

    private Fixture fixture(String home, String away) {
        Fixture f = new Fixture();
        f.setHomeTeamName(home);
        f.setAwayTeamName(away);
        f.setStatusShort("FT");
        return f;
    }
}
