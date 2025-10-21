package com.playerdatatracking.operations.IndelxalData;

import com.playerdatatracking.services.PlayerJsonIngestService;

public class IngestRawData {

	private final PlayerJsonIngestService svc;

public IngestRawData(PlayerJsonIngestService svc) { this.svc = svc; }

	public void ejecutar() throws Exception {
		String root = "src/main/resources/json/apiFotball/players";
		boolean purgeBeforeRun = true;
		int parallelism = 4;
		svc.ingestAllPlayers(root, purgeBeforeRun, parallelism);
	}
}
