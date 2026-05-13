package com.playerdatatracking.operations;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.Torneo;
import com.playerdatatracking.exceptions.db.PlayerDataDBException;
import com.playerdatatracking.operations.IndelxalData.indexal.GetStudiedLeagues;
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
class GetStudiedLeaguesTest {

    @Mock
    private PlayerDataClient pdClient;

    @InjectMocks
    private GetStudiedLeagues getStudiedLeagues;

    @Test
    void ejecutar_returnsStudiedLeagues() throws Exception {
        List<Torneo> leagues = List.of(torneo(140L, "La Liga"), torneo(39L, "Premier League"));
        when(pdClient.getStudiedLeagues()).thenReturn(leagues);

        GenericResponse<Torneo> response = getStudiedLeagues.ejecutar();

        assertThat(response.getCODE()).isEqualTo(Constants.CODE_OK);
        assertThat(response.getDescription()).isEqualTo("OK");
        assertThat(response.getEntityList()).hasSize(2);
        assertThat(response.getEntityList().get(0).getName()).isEqualTo("La Liga");
    }

    @Test
    void ejecutar_noStudiedLeagues_returnsEmptyList() throws Exception {
        when(pdClient.getStudiedLeagues()).thenReturn(List.of());

        GenericResponse<Torneo> response = getStudiedLeagues.ejecutar();

        assertThat(response.getCODE()).isEqualTo(Constants.CODE_OK);
        assertThat(response.getEntityList()).isEmpty();
    }

    @Test
    void ejecutar_dbException_propagates() throws Exception {
        when(pdClient.getStudiedLeagues()).thenThrow(new PlayerDataDBException("DB unavailable"));

        assertThatThrownBy(() -> getStudiedLeagues.ejecutar())
                .isInstanceOf(PlayerDataDBException.class)
                .hasMessageContaining("DB unavailable");
    }

    @Test
    void ejecutar_callsPdClientExactlyOnce() throws Exception {
        when(pdClient.getStudiedLeagues()).thenReturn(List.of());

        getStudiedLeagues.ejecutar();

        verify(pdClient, times(1)).getStudiedLeagues();
        verifyNoMoreInteractions(pdClient);
    }

    private Torneo torneo(Long id, String name) {
        Torneo t = new Torneo();
        t.setId(id);
        t.setName(name);
        t.setStudied(true);
        return t;
    }
}
