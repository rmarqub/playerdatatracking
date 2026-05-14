package com.playerdatatracking.operations.IndelxalData.indexal;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.indexaldata.Torneo;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.GenericResponse;

@Component
public class UpdateStudiedLeagues {

    @Autowired
    private PlayerDataClient pdClient;

    public GenericResponse<String> ejecutar(GenericRequest request) throws Exception {
        GenericResponse<String> response = new GenericResponse<>();

        Set<Long> studiedIds = request.getLeagueIds() != null
                ? Set.copyOf(request.getLeagueIds())
                : Set.of();

        List<Torneo> all = pdClient.getAllTorneos();
        for (Torneo t : all) {
            t.setStudied(studiedIds.contains(t.getId()));
        }
        pdClient.saveAllTorneos(all);

        response.setCODE(Constants.CODE_OK);
        response.setDescription("Ligas actualizadas correctamente");
        return response;
    }
}
