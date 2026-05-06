package com.playerdatatracking.operations.IndelxalData;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.services.PlayerJsonIngestService;

@Component
public class TransformRawToStats {

    @Autowired
    private PlayerJsonIngestService svc;

    @Autowired
    private PlayerDataClient pdClient;

    public void ejecutar(GenericRequest request) throws Exception {
        String requestedSeason = request.getSeason();
        String effectiveSeason = (requestedSeason != null && !requestedSeason.trim().isEmpty())
                ? requestedSeason.trim()
                : pdClient.getParam(Constants.ACTUAL_APF_SEASON).getValue();

        svc.transformFromRaw(effectiveSeason);
    }
}
