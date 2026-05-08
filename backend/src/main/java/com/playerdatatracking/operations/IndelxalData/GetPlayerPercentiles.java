package com.playerdatatracking.operations.IndelxalData;

import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.PlayerPercentile;
import com.playerdatatracking.exceptions.operations.PlayerInputException;
import com.playerdatatracking.repositories.indexaldata.PlayerPercentileRepository;
import com.playerdatatracking.responses.GenericResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GetPlayerPercentiles {

    @Autowired
    private PlayerPercentileRepository percentileRepository;

    public GenericResponse<PlayerPercentile> ejecutar(Long indexId, String season) throws PlayerInputException {
        GenericResponse<PlayerPercentile> response = new GenericResponse<>();

        if (indexId == null)
            throw new PlayerInputException("Se requiere el indexId del jugador");

        List<PlayerPercentile> rows = (season != null && !season.isBlank())
                ? percentileRepository.findByIndexIdAndSeason(indexId, season)
                : percentileRepository.findByIndexId(indexId);

        if (rows.isEmpty()) {
            response.setCODE(Constants.CODE_ERR_NO_PLAYER_FOUND);
            response.setDescription("Sin percentiles para indexId=" + indexId
                    + (season != null ? " season=" + season : ""));
            return response;
        }

        response.setCODE(Constants.CODE_OK);
        response.setDescription("OK");
        response.setEntityList(rows);
        return response;
    }
}
