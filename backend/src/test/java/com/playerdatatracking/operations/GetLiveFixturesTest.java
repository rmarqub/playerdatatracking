package com.playerdatatracking.operations;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.Fixture;
import com.playerdatatracking.exceptions.db.PlayerDataDBException;
import com.playerdatatracking.operations.IndelxalData.GetLiveFixtures;
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
class GetLiveFixturesTest {

    @Mock
    private PlayerDataClient pdClient;

    @InjectMocks
    private GetLiveFixtures getLiveFixtures;

    @Test
    void ejecutar_returnsLiveFixtures() throws Exception {
        List<Fixture> live = List.of(liveFixture("1H"), liveFixture("2H"), liveFixture("HT"));
        when(pdClient.getLiveFixtures()).thenReturn(live);

        GenericResponse<Fixture> response = getLiveFixtures.ejecutar();

        assertThat(response.getCODE()).isEqualTo(Constants.CODE_OK);
        assertThat(response.getDescription()).isEqualTo("OK");
        assertThat(response.getEntityList()).hasSize(3);
        verify(pdClient).getLiveFixtures();
    }

    @Test
    void ejecutar_noLiveMatches_returnsEmptyList() throws Exception {
        when(pdClient.getLiveFixtures()).thenReturn(List.of());

        GenericResponse<Fixture> response = getLiveFixtures.ejecutar();

        assertThat(response.getCODE()).isEqualTo(Constants.CODE_OK);
        assertThat(response.getEntityList()).isEmpty();
    }

    @Test
    void ejecutar_dbException_propagates() throws Exception {
        when(pdClient.getLiveFixtures()).thenThrow(new PlayerDataDBException("connection refused"));

        assertThatThrownBy(() -> getLiveFixtures.ejecutar())
                .isInstanceOf(PlayerDataDBException.class)
                .hasMessageContaining("connection refused");
    }

    @Test
    void ejecutar_callsPdClientExactlyOnce() throws Exception {
        when(pdClient.getLiveFixtures()).thenReturn(List.of());

        getLiveFixtures.ejecutar();

        verify(pdClient, times(1)).getLiveFixtures();
        verifyNoMoreInteractions(pdClient);
    }

    private Fixture liveFixture(String statusShort) {
        Fixture f = new Fixture();
        f.setStatusShort(statusShort);
        f.setHomeTeamName("Team A");
        f.setAwayTeamName("Team B");
        return f;
    }
}
