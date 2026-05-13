package com.playerdatatracking.operations.IndelxalData.player;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.FixturePlayerStats;
import com.playerdatatracking.entities.indexaldata.Player;
import com.playerdatatracking.exceptions.operations.PlayerInputException;
import com.playerdatatracking.repositories.indexaldata.FixturePlayerStatsRepository;
import com.playerdatatracking.repositories.indexaldata.PlayerRepository;
import com.playerdatatracking.responses.GenericResponse;
import com.playerdatatracking.responses.PlayerAbsenceDays;

@Component
public class GetPlayerAbsenceDays {

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private FixturePlayerStatsRepository statsRepository;

    public GenericResponse<PlayerAbsenceDays> ejecutar(Long indexId) throws Exception {
        GenericResponse<PlayerAbsenceDays> response = new GenericResponse<>();

        if (indexId == null)
            throw new PlayerInputException("Se requiere el indexId del jugador");

        Optional<Player> playerOpt = playerRepository.findFirstByIndexIdOrderByIdDesc(indexId);
        if (playerOpt.isEmpty())
            throw new PlayerInputException("No se encontró jugador con indexId=" + indexId);

        Player player = playerOpt.get();
        PlayerAbsenceDays result = new PlayerAbsenceDays();
        result.setPlayerId(indexId);
        result.setPlayerName(player.getFullname());
        result.setInjured(player.getInjured());

        Optional<FixturePlayerStats> lastAppearance = statsRepository.findLastAppearanceByPlayerId(indexId);
        if (lastAppearance.isPresent()) {
            FixturePlayerStats stats = lastAppearance.get();
            result.setLastMatchDate(stats.getFixture().getMatchDate());
            result.setLastFixtureId(stats.getFixture().getId());
            LocalDate lastMatch = stats.getFixture().getMatchDate().toLocalDate();
            result.setDaysAbsent(ChronoUnit.DAYS.between(lastMatch, LocalDate.now()));
        }

        response.setCODE(Constants.CODE_OK);
        response.setDescription("OK");
        response.setEntity(result);
        return response;
    }
}
