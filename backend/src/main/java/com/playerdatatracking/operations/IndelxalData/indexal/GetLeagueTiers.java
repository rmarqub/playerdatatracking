package com.playerdatatracking.operations.IndelxalData.indexal;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.LeagueTier;
import com.playerdatatracking.entities.indexaldata.Pais;
import com.playerdatatracking.entities.indexaldata.Torneo;
import com.playerdatatracking.responses.GenericResponse;
import com.playerdatatracking.responses.LeagueTierInfo;

@Component
public class GetLeagueTiers {

    @Autowired
    private PlayerDataClient pdClient;

    public GenericResponse<LeagueTierInfo> ejecutar() throws Exception {
        GenericResponse<LeagueTierInfo> response = new GenericResponse<>();

        List<Pais> paises = pdClient.getAllPaises();
        Map<Integer, String> paisMap = paises.stream()
                .collect(Collectors.toMap(Pais::getId, Pais::getName));

        List<Torneo> torneos = pdClient.getAllTorneos();
        Map<Long, LeagueTier> tierMap = pdClient.getAllLeagueTiers().stream()
                .collect(Collectors.toMap(LeagueTier::getTorneoId, lt -> lt));

        List<LeagueTierInfo> infoList = torneos.stream()
                .map(t -> {
                    LeagueTier lt = tierMap.get(t.getId());
                    Integer tier       = lt != null ? lt.getTier()       : 0;
                    Double  tierFactor = lt != null ? lt.getTierFactor()  : 0.40;
                    String  notes      = lt != null ? lt.getNotes()       : null;
                    String  paisName   = t.getPais() != null ? paisMap.get(t.getPais()) : null;
                    return new LeagueTierInfo(t.getId(), t.getName(), t.getPais(), paisName,
                                             tier, tierFactor, notes);
                })
                .sorted(Comparator
                        .comparingInt((LeagueTierInfo lti) -> lti.getTier() != null ? lti.getTier() : 0)
                        .thenComparing(lti -> lti.getPaisName() != null ? lti.getPaisName() : "￿"))
                .collect(Collectors.toList());

        response.setCODE(Constants.CODE_OK);
        response.setDescription("OK");
        response.setEntityList(infoList);
        return response;
    }
}
