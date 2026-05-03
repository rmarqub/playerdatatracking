package com.playerdatatracking.operations.IndelxalData;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.services.PlayerJsonIngestService;

@Component
public class IngestRawData {

	@Autowired
	private PlayerJsonIngestService svc;

	public void ejecutar(GenericRequest request) throws Exception {
		String root = "src/main/resources/json/apiFotball/players";
		boolean purgeBeforeRun = false;

		if (request.getPurgeBeforeRun().equalsIgnoreCase("true"))
			purgeBeforeRun = true;
		int parallelism = request.getThreads();
		svc.ingestAllPlayers(root, purgeBeforeRun, parallelism);
	}
}
