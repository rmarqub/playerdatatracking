package com.playerdatatracking.operations.IndelxalData;

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
import com.playerdatatracking.requests.ContextualAnalysisRequest;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.ContextualAnalysisData;
import com.playerdatatracking.responses.GenericResponse;

@Component
public class SaveContextualAnalysis {

    @Autowired
    private FixtureContextualAnalysisRepository analysisRepository;

    public GenericResponse<ContextualAnalysisData> ejecutar(GenericRequest request) throws Exception {
        GenericResponse<ContextualAnalysisData> response = new GenericResponse<>();

        ContextualAnalysisRequest req = request.getContextualAnalysis();
        if (req == null || req.getFixtureId() == null)
            throw new PlayerInputException("Se requiere el id del partido en el análisis contextual");

        Optional<FixtureContextualAnalysis> existing = analysisRepository.findByFixtureId(req.getFixtureId());
        FixtureContextualAnalysis entity = existing.orElseGet(FixtureContextualAnalysis::new);

        entity.setFixtureId(req.getFixtureId());

        if (req.getHomeCurrentForm()       != null) entity.setHomeCurrentForm(req.getHomeCurrentForm());
        if (req.getHomeStadiumAtmosphere() != null) entity.setHomeStadiumAtmosphere(req.getHomeStadiumAtmosphere());
        if (req.getHomeDefensiveBlock()    != null) entity.setHomeDefensiveBlock(req.getHomeDefensiveBlock());
        if (req.getHomeOffensiveRhythm()   != null) entity.setHomeOffensiveRhythm(req.getHomeOffensiveRhythm());
        if (req.getHomeTeamNeeds()         != null) entity.setHomeTeamNeeds(req.getHomeTeamNeeds());
        if (req.getHomeSetPieces()         != null) entity.setHomeSetPieces(req.getHomeSetPieces());
        if (req.getHomeFatigue()           != null) entity.setHomeFatigue(req.getHomeFatigue());
        entity.setHomeUnavailablePlayers(joinPlayers(req.getHomeUnavailablePlayers()));

        if (req.getAwayCurrentForm()       != null) entity.setAwayCurrentForm(req.getAwayCurrentForm());
        if (req.getAwayStadiumAtmosphere() != null) entity.setAwayStadiumAtmosphere(req.getAwayStadiumAtmosphere());
        if (req.getAwayDefensiveBlock()    != null) entity.setAwayDefensiveBlock(req.getAwayDefensiveBlock());
        if (req.getAwayOffensiveRhythm()   != null) entity.setAwayOffensiveRhythm(req.getAwayOffensiveRhythm());
        if (req.getAwayTeamNeeds()         != null) entity.setAwayTeamNeeds(req.getAwayTeamNeeds());
        if (req.getAwaySetPieces()         != null) entity.setAwaySetPieces(req.getAwaySetPieces());
        if (req.getAwayFatigue()           != null) entity.setAwayFatigue(req.getAwayFatigue());
        entity.setAwayUnavailablePlayers(joinPlayers(req.getAwayUnavailablePlayers()));

        entity.setNotes(req.getNotes());
        if (req.getBaseHomeWin() != null) entity.setBaseHomeWin(req.getBaseHomeWin());
        if (req.getBaseDraw()    != null) entity.setBaseDraw(req.getBaseDraw());
        if (req.getBaseAwayWin() != null) entity.setBaseAwayWin(req.getBaseAwayWin());

        FixtureContextualAnalysis saved = analysisRepository.save(entity);

        ContextualAnalysisData data = toData(saved);
        response.setCODE(Constants.CODE_OK);
        response.setDescription("OK");
        response.setEntity(data);
        return response;
    }

    private String joinPlayers(List<String> players) {
        if (players == null || players.isEmpty()) return null;
        return String.join(",", players);
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
