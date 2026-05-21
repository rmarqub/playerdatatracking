package com.playerdatatracking.operations.IndelxalData.indexal;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.requests.LeagueTierEntry;
import com.playerdatatracking.responses.GenericResponse;

@Component
public class UpdateLeagueTiers {

    @Autowired
    private PlayerDataClient pdClient;

    public GenericResponse<String> ejecutar(GenericRequest request) throws Exception {
        GenericResponse<String> response = new GenericResponse<>();

        List<LeagueTierEntry> entries = request.getLeagueTiers();
        if (entries == null || entries.isEmpty()) {
            response.setCODE(Constants.CODE_OK);
            response.setDescription("Sin cambios que guardar");
            return response;
        }

        int saved = pdClient.upsertLeagueTiers(entries);

        response.setCODE(Constants.CODE_OK);
        response.setDescription("Tiers actualizados: " + saved + " liga(s)");
        return response;
    }
}
