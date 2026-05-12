package com.playerdatatracking.operations.IndelxalData;

import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.PlayerPercentile;
import com.playerdatatracking.entities.indexaldata.Torneo;
import com.playerdatatracking.entities.indexaldata.DTO.PlayerPercentileDTO;
import com.playerdatatracking.exceptions.operations.PlayerInputException;
import com.playerdatatracking.repositories.indexaldata.PlayerPercentileRepository;
import com.playerdatatracking.repositories.indexaldata.TorneoRepository;
import com.playerdatatracking.responses.GenericResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class GetPlayerPercentiles {

    @Autowired
    private PlayerPercentileRepository percentileRepository;

    @Autowired
    private TorneoRepository torneoRepository;

    public GenericResponse<PlayerPercentileDTO> ejecutar(Long indexId, String season) throws PlayerInputException {
        GenericResponse<PlayerPercentileDTO> response = new GenericResponse<>();

        if (indexId == null) {
            throw new PlayerInputException("Se requiere el indexId del jugador");
        }

        List<PlayerPercentile> rows = (season != null && !season.isBlank())
                ? percentileRepository.findByIndexIdAndSeason(indexId, season)
                : percentileRepository.findByIndexId(indexId);

        if (rows.isEmpty()) {
            response.setCODE(Constants.CODE_ERR_NO_PLAYER_FOUND);
            response.setDescription("Sin percentiles para indexId=" + indexId
                    + (season != null ? " season=" + season : ""));
            return response;
        }

        List<Long> leagueIds = rows.stream()
                .map(PlayerPercentile::getLeagueId)
                .filter(id -> id != null && id != 0)
                .map(Integer::longValue)
                .distinct()
                .toList();

        Map<Integer, String> leagueNames = torneoRepository.findAllById(leagueIds)
                .stream()
                .collect(Collectors.toMap(
                        torneo -> torneo.getId().intValue(),
                        Torneo::getName
                ));

        List<PlayerPercentileDTO> dtoRows = rows.stream()
                .map(row -> toDto(row, leagueNames))
                .toList();

        response.setCODE(Constants.CODE_OK);
        response.setDescription("OK");
        response.setEntityList(dtoRows);
        return response;
    }

    private PlayerPercentileDTO toDto(PlayerPercentile row, Map<Integer, String> leagueNames) {
        PlayerPercentileDTO dto = new PlayerPercentileDTO();

        dto.setId(row.getId());
        dto.setPlayerId(row.getPlayerId());
        dto.setIndexId(row.getIndexId());
        dto.setLeagueId(row.getLeagueId());
        dto.setSeason(row.getSeason());

        if (row.getLeagueId() != null && row.getLeagueId() == 0) {
            dto.setLeagueName("Global");
        } else {
            dto.setLeagueName(
                    leagueNames.getOrDefault(row.getLeagueId(), "Liga " + row.getLeagueId())
            );
        }

        dto.setPctMinutes(row.getPctMinutes());
        dto.setPctRating(row.getPctRating());
        dto.setPctGoalsP90(row.getPctGoalsP90());
        dto.setPctAssistsP90(row.getPctAssistsP90());
        dto.setPctShotsTotalP90(row.getPctShotsTotalP90());
        dto.setPctShotsOnP90(row.getPctShotsOnP90());
        dto.setPctPassesTotalP90(row.getPctPassesTotalP90());
        dto.setPctPassesKeyP90(row.getPctPassesKeyP90());
        dto.setPctPassAccuracy(row.getPctPassAccuracy());
        dto.setPctTacklesP90(row.getPctTacklesP90());
        dto.setPctInterceptionsP90(row.getPctInterceptionsP90());
        dto.setPctDuelsWon(row.getPctDuelsWon());
        dto.setPctDribblesSuccess(row.getPctDribblesSuccess());
        dto.setPctFoulsDrawnP90(row.getPctFoulsDrawnP90());

        return dto;
    }
}
