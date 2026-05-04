package com.playerdatatracking.operations.IndelxalData;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.Club;
import com.playerdatatracking.entities.indexaldata.Fixture;
import com.playerdatatracking.exceptions.operations.PlayerInputException;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.GenericResponse;

@Component
public class SearchFixtures {

    @Autowired
    private PlayerDataClient pdClient;

    public GenericResponse<Fixture> ejecutar(GenericRequest request) throws Exception {
        GenericResponse<Fixture> response = new GenericResponse<>();

        String nombre = request.getNombre() != null ? request.getNombre().trim() : null;
        List<Long> leagueIds = request.getLeagueIds();

        boolean hasTeam = nombre != null && !nombre.isEmpty();
        boolean hasLeagues = leagueIds != null && !leagueIds.isEmpty();

        if (!hasTeam && !hasLeagues)
            throw new PlayerInputException("Se requiere nombre de equipo o al menos una liga para buscar partidos");

        List<Long> teamIds = null;
        if (hasTeam) {
            List<Club> clubs = pdClient.findClubsByNameContaining(nombre);
            if (clubs.isEmpty()) {
                response.setCODE(Constants.CODE_OK);
                response.setDescription("OK");
                response.setEntityList(Collections.emptyList());
                return response;
            }
            teamIds = clubs.stream().map(Club::getId).collect(Collectors.toList());
        }

        List<Integer> leagueIntIds = hasLeagues
                ? leagueIds.stream().map(Long::intValue).collect(Collectors.toList())
                : null;

        List<Fixture> fixtures;
        if (teamIds != null && leagueIntIds != null) {
            fixtures = pdClient.searchFixturesByTeamIdsAndLeagueIds(teamIds, leagueIntIds);
        } else if (teamIds != null) {
            fixtures = pdClient.searchFixturesByTeamIds(teamIds);
        } else {
            fixtures = pdClient.searchFixturesByLeagueIds(leagueIntIds);
        }

        response.setCODE(Constants.CODE_OK);
        response.setDescription("OK");
        response.setEntityList(fixtures);
        return response;
    }
}
