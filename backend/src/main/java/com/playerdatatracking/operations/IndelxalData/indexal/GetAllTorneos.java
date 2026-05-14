package com.playerdatatracking.operations.IndelxalData.indexal;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.Pais;
import com.playerdatatracking.entities.indexaldata.Torneo;
import com.playerdatatracking.responses.GenericResponse;
import com.playerdatatracking.responses.TorneoInfo;

@Component
public class GetAllTorneos {

    @Autowired
    private PlayerDataClient pdClient;

    public GenericResponse<TorneoInfo> ejecutar() throws Exception {
        GenericResponse<TorneoInfo> response = new GenericResponse<>();

        List<Pais> paises = pdClient.getAllPaises();
        Map<Integer, String> paisMap = paises.stream()
                .collect(Collectors.toMap(Pais::getId, Pais::getName));

        List<Torneo> torneos = pdClient.getAllTorneos();
        List<TorneoInfo> infoList = torneos.stream()
                .map(t -> new TorneoInfo(
                        t.getId(),
                        t.getName(),
                        Boolean.TRUE.equals(t.getStudied()),
                        t.getPais(),
                        t.getPais() != null ? paisMap.get(t.getPais()) : null
                ))
                .sorted(Comparator.comparing(
                        (TorneoInfo ti) -> ti.getPaisName() != null ? ti.getPaisName() : "￿"
                ))
                .collect(Collectors.toList());

        response.setCODE(Constants.CODE_OK);
        response.setDescription("OK");
        response.setEntityList(infoList);
        return response;
    }
}
