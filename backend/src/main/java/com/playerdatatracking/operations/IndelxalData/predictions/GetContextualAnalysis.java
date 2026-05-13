package com.playerdatatracking.operations.IndelxalData.predictions;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.FixtureContextualAnalysis;
import com.playerdatatracking.exceptions.operations.PlayerInputException;
import com.playerdatatracking.repositories.indexaldata.FixtureContextualAnalysisRepository;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.ContextualAnalysisData;
import com.playerdatatracking.responses.GenericResponse;

@Component
public class GetContextualAnalysis {

    @Autowired
    private FixtureContextualAnalysisRepository analysisRepository;

    public GenericResponse<ContextualAnalysisData> ejecutar(GenericRequest request) throws Exception {
        GenericResponse<ContextualAnalysisData> response = new GenericResponse<>();

        if (request.getId() == null)
            throw new PlayerInputException("Se requiere el id del partido");

        Optional<FixtureContextualAnalysis> opt = analysisRepository.findByFixtureId(request.getId());

        if (opt.isEmpty()) {
            response.setCODE(Constants.CODE_OK);
            response.setDescription("OK");
            return response;
        }

        ContextualAnalysisData data = toData(opt.get());
        response.setCODE(Constants.CODE_OK);
        response.setDescription("OK");
        response.setEntity(data);
        return response;
    }

    private List<String> splitPlayers(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        return Arrays.asList(raw.split(","));
    }

    private ContextualAnalysisData toData(FixtureContextualAnalysis e) {
        ContextualAnalysisData d = new ContextualAnalysisData();
        d.setFixtureId(e.getFixtureId());
        d.setHomeCurrentForm(e.getHomeCurrentForm());
        d.setHomeStadiumAtmosphere(e.getHomeStadiumAtmosphere());
        d.setHomeDefensiveBlock(e.getHomeDefensiveBlock());
        d.setHomeOffensiveRhythm(e.getHomeOffensiveRhythm());
        d.setHomeTeamNeeds(e.getHomeTeamNeeds());
        d.setHomeSetPieces(e.getHomeSetPieces());
        d.setHomeFatigue(e.getHomeFatigue());
        d.setHomeUnavailablePlayers(splitPlayers(e.getHomeUnavailablePlayers()));
        d.setAwayCurrentForm(e.getAwayCurrentForm());
        d.setAwayStadiumAtmosphere(e.getAwayStadiumAtmosphere());
        d.setAwayDefensiveBlock(e.getAwayDefensiveBlock());
        d.setAwayOffensiveRhythm(e.getAwayOffensiveRhythm());
        d.setAwayTeamNeeds(e.getAwayTeamNeeds());
        d.setAwaySetPieces(e.getAwaySetPieces());
        d.setAwayFatigue(e.getAwayFatigue());
        d.setAwayUnavailablePlayers(splitPlayers(e.getAwayUnavailablePlayers()));
        d.setNotes(e.getNotes());
        d.setUpdatedAt(e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : null);
        d.setBaseHomeWin(e.getBaseHomeWin());
        d.setBaseDraw(e.getBaseDraw());
        d.setBaseAwayWin(e.getBaseAwayWin());
        return d;
    }
}
