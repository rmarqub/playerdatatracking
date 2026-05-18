package com.playerdatatracking.operations.IndelxalData.indexal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.services.PlayerJsonIngestService;

@Component
public class IngestRawData {

	@Autowired
	private PlayerJsonIngestService svc;

	@Autowired
	private PlayerDataClient pdClient;

	@Value("${players.json.directory:src/main/resources/json/apiFotball/players/}")
	private String directoryPath;

	public void ejecutar(GenericRequest request) throws Exception {
		String root = directoryPath;
		boolean purgeBeforeRun = false;

		if (request.getPurgeBeforeRun().equalsIgnoreCase("true"))
			purgeBeforeRun = true;
		int parallelism = request.getThreads();

		String requestedSeason = request.getSeason();
		String effectiveSeason = (requestedSeason != null && !requestedSeason.trim().isEmpty())
				? requestedSeason.trim()
				: pdClient.getParam(Constants.ACTUAL_APF_SEASON).getValue();

		svc.ingestAllPlayers(root, purgeBeforeRun, parallelism, effectiveSeason);
	}
}
