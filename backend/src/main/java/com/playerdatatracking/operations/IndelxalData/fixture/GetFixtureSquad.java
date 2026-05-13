package com.playerdatatracking.operations.IndelxalData.fixture;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.Fixture;
import com.playerdatatracking.entities.indexaldata.Player;
import com.playerdatatracking.exceptions.operations.PlayerInputException;
import com.playerdatatracking.repositories.indexaldata.FixtureRepository;
import com.playerdatatracking.repositories.indexaldata.PlayerRepository;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.FixtureSquadData;
import com.playerdatatracking.responses.GenericResponse;
import com.playerdatatracking.responses.SquadPlayerEntry;

@Component
public class GetFixtureSquad {

    @Autowired
    private FixtureRepository fixtureRepository;

    @Autowired
    private PlayerRepository playerRepository;

    public GenericResponse<FixtureSquadData> ejecutar(GenericRequest request) throws Exception {
        GenericResponse<FixtureSquadData> response = new GenericResponse<>();

        if (request.getId() == null)
            throw new PlayerInputException("Se requiere el id del partido");

        Optional<Fixture> optFixture = fixtureRepository.findById(request.getId());
        if (optFixture.isEmpty())
            throw new PlayerInputException("Partido no encontrado");

        Fixture fixture = optFixture.get();

        FixtureSquadData data = new FixtureSquadData();
        data.setHomePlayers(toEntries(playerRepository.findByTeamId(fixture.getHomeTeamId())));
        data.setAwayPlayers(toEntries(playerRepository.findByTeamId(fixture.getAwayTeamId())));

        response.setCODE(Constants.CODE_OK);
        response.setDescription("OK");
        response.setEntity(data);
        return response;
    }

    private List<SquadPlayerEntry> toEntries(List<Player> players) {
        return players.stream()
            .map(p -> {
                String name = p.getFullname();
                if (name == null || name.isBlank()) {
                    String fn = p.getFirstname() != null ? p.getFirstname() : "";
                    String ln = p.getLastname()  != null ? p.getLastname()  : "";
                    name = (fn + " " + ln).trim();
                }
                return new SquadPlayerEntry(p.getId(), name);
            })
            .filter(e -> e.getPlayerName() != null && !e.getPlayerName().isBlank())
            .collect(Collectors.toList());
    }
}
