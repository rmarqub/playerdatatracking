package com.playerdatatracking.operations.IndelxalData;

import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.services.PlayerJsonIngestService;

public class IngestRawData {

	private final PlayerJsonIngestService svc;

public IngestRawData(PlayerJsonIngestService svc) { this.svc = svc; }

	public void ejecutar(GenericRequest request) throws Exception {
		String root = "src/main/resources/json/apiFotball/players";
		boolean purgeBeforeRun = false;

		if (request.getPurgeBeforeRun().equalsIgnoreCase("true"))
			purgeBeforeRun = true;
		int parallelism = request.getThreads();
		svc.ingestAllPlayers(root, purgeBeforeRun, parallelism);
	}
}
