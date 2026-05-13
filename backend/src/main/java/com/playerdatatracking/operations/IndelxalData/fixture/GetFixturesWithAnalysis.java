package com.playerdatatracking.operations.IndelxalData.fixture;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.Fixture;
import com.playerdatatracking.repositories.indexaldata.FixtureContextualAnalysisRepository;
import com.playerdatatracking.repositories.indexaldata.FixtureRepository;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.GenericResponse;

@Component
public class GetFixturesWithAnalysis {

    @Autowired private FixtureContextualAnalysisRepository analysisRepository;
    @Autowired private FixtureRepository                   fixtureRepository;

    public GenericResponse<Fixture> ejecutar(GenericRequest request) throws Exception {
        GenericResponse<Fixture> response = new GenericResponse<>();

        List<Long> fixtureIds = analysisRepository.findAll().stream()
                .map(a -> a.getFixtureId())
                .collect(Collectors.toList());

        if (fixtureIds.isEmpty()) {
            response.setCODE(Constants.CODE_OK);
            response.setDescription("OK");
            response.setEntityList(Collections.emptyList());
            return response;
        }

        List<Fixture> fixtures = fixtureRepository.findAllById(fixtureIds).stream()
                .sorted((a, b) -> {
                    if (a.getMatchDate() == null) return 1;
                    if (b.getMatchDate() == null) return -1;
                    return b.getMatchDate().compareTo(a.getMatchDate());
                })
                .collect(Collectors.toList());

        fixtures.forEach(f -> f.setHasAnalysis(true));

        response.setCODE(Constants.CODE_OK);
        response.setDescription("OK");
        response.setEntityList(fixtures);
        return response;
    }
}
