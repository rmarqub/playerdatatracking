package com.playerdatatracking.operations.IndelxalData.player;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.sql.Timestamp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerdatatracking.clients.ApiFootballClient;
import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.common.Methods;
import com.playerdatatracking.entities.indexaldata.Club;
import com.playerdatatracking.entities.indexaldata.ClubInLeague;
import com.playerdatatracking.entities.indexaldata.Pais;
import com.playerdatatracking.entities.indexaldata.Player;
import com.playerdatatracking.entities.indexaldata.Torneo;
import com.playerdatatracking.entities.keys.Keys;
import com.playerdatatracking.exceptions.apikeys.ApiKeyManagementException;
import com.playerdatatracking.exceptions.file.NotCreatedJsonFileResponse;
import com.playerdatatracking.exceptions.file.NotFilledJsonFileResponse;
import com.playerdatatracking.operations.apikeys.KeysManagement;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.GenericResponse;

@Component
public class UpdatePlayersData {

	@Autowired
	private PlayerDataClient pdClient;
	private ApiFootballClient restClient;
	@Autowired
	private KeysManagement keyMethods;
	@Autowired
	private Environment env;

	@Value("${players.json.directory:src/main/resources/json/apiFotball/players/}")
	private String directoryPath;
	@Value("${leagues.json.directory:src/main/resources/json/apiFotball/leagues/}")
	private String leaguesPath;

	String excludedLeague = "leagues.json";
	private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
	private static final long MAX_BYTES = 5L * 1024 * 1024;
	private static final ObjectMapper objectMapper = new ObjectMapper();
	private static final DateTimeFormatter LOG_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private GenericResponse<Player> response = new GenericResponse();
	private Methods methods;
	DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private record ImageResult(byte[] bytes, String contentType) {}

	public void setPdClient(PlayerDataClient pdClient) {
		this.pdClient = pdClient;
	}

	public void setEnv(Environment env) {
		this.env = env;
	}

	public GenericResponse<Player> ejecutar(GenericRequest request) throws Exception {
		restClient = new ApiFootballClient();
		methods = new Methods();
		List<Club> updatedClubs = new ArrayList<Club>();
		if (request.getRestUpdate() != null && request.getRestUpdate().equals("true")) {
			String requestedSeason = request.getSeason();
			String actualSeason = (requestedSeason != null && !requestedSeason.trim().isEmpty())
					? requestedSeason.trim()
					: pdClient.getParam(Constants.ACTUAL_APF_SEASON).getValue();
			List<Torneo> studiedLeagues = pdClient.getStudiedLeagues();
			List<Club> clubList = pdClient.getAllClubs();
			if (clubList.size() > 0) {
				Keys apiKey = keyMethods.nextKey();
				if (apiKey == null)
					throw new ApiKeyManagementException("no hay almacenada ninguna key valida");
				for (Club club : clubList) {
					List<ClubInLeague> cilList = pdClient.findCILsByClub(club.getId());
					if (cilList != null && cilList.size() > 0) {
						for (ClubInLeague cil : cilList) {
							Torneo auxLeague = pdClient.getTorneoById(cil.getTorneoId());
							if (auxLeague != null && studiedLeagues.contains(auxLeague) && !updatedClubs.contains(club)) {
								updatedClubs.add(club);
								HashMap<String, String> queryParams = new HashMap<>();
								queryParams.put("season", actualSeason);
								queryParams.put("page", "1");
								int actualPage = 1;
								queryParams.put("team", club.getId().toString());
								String responsePath = "";
								if (keyMethods.checkReadiness(apiKey)) {
									responsePath = restClient.getPlayersPaged(queryParams, apiKey.getValor(), club.getNombre(), "1");
									methods.checkGoodPlayersCall(responsePath, queryParams, apiKey.getValor(), club.getNombre(), "1");
									System.out.println("Club: " + club.getNombre() + ", Page: " + actualPage);
									keyMethods.useKey(apiKey);
									methods.sleep(180);
								} else
									throw new ApiKeyManagementException("error al intentar usar una key no disponible");
								int totalofPages = methods.getTotalOfPagesResponse(responsePath);
								while (actualPage < totalofPages) {
									actualPage++;
									queryParams.put("page", Integer.toString(actualPage));
									if (keyMethods.checkReadiness(apiKey)) {
										responsePath = restClient.getPlayersPaged(queryParams, apiKey.getValor(), club.getNombre(), Integer.toString(actualPage));
										methods.checkGoodPlayersCall(responsePath, queryParams, apiKey.getValor(), club.getNombre(), Integer.toString(actualPage));
										System.out.println("Club: " + club.getNombre() + ", Page: " + actualPage + "/" + totalofPages + " stored");
										keyMethods.useKey(apiKey);
										methods.sleep(180);
									} else {
										throw new ApiKeyManagementException("error al intentar usar una key no disponible");
									}
								}
							}
						}
					}
				}
			}
		}
		if (request.getUpdate() != null && request.getUpdate().equals("true")) {
			String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
			Path logDir = Paths.get("logs");
			Files.createDirectories(logDir);
			Path logPath = logDir.resolve("players_update_" + ts + ".txt");

			List<Path> directories = Files.list(Paths.get(directoryPath))
					.filter(Files::isDirectory)
					.collect(Collectors.toList());
			if (directories.isEmpty())
				throw new NotCreatedJsonFileResponse("No hay archivos de jugadores disponibles para realizar la carga de datos");

			// Pre-cargar mapa de nacionalidades una sola vez en lugar de una query por jugador
			Map<String, Integer> countryMap = new HashMap<>();
			for (Pais p : pdClient.getAllPaises()) {
				if (p.getName() != null) countryMap.put(p.getName(), p.getId());
			}

			// Cargar imagen estándar una sola vez en lugar de hacerlo por jugador
			byte[] standardImage = null;
			final String standardCt = "image/jpeg";
			try (InputStream in = getClass().getResourceAsStream("/images/standard-pic.jpg")) {
				if (in != null) standardImage = in.readAllBytes();
				else log(logPath, "No se encontró la imagen estándar en resources/images/standard-pic.jpg");
			} catch (Exception e) {
				log(logPath, "Error cargando imagen estándar: " + e.getMessage());
			}

			ExecutorService imagePool = Executors.newFixedThreadPool(8);
			try {
				for (Path directory : directories) {
					List<Path> fileList;
					try (Stream<Path> files = Files.list(directory)) {
						fileList = files.filter(Files::isRegularFile).collect(Collectors.toList());
					}

					// Jugadores existentes del equipo cargados en bulk (lazy, al ver el primer teamId válido)
					Map<Long, Player> existingByIndexId = null;
					List<Player> toSave = new ArrayList<>();
					List<CompletableFuture<Void>> imageFutures = new ArrayList<>();

					for (Path path : fileList) {
						String filePath = path.toString();
						System.out.println("  Archivo: " + filePath);
						log(logPath, "Leyendo archivo: " + filePath);
						try {
							File file = path.toFile();
							if (!file.exists())
								throw new NotCreatedJsonFileResponse("error al crear un json de respuesta, el archivo no ha sido creado o no se ha guardado correctamente");
							if (file.length() == 0)
								throw new NotFilledJsonFileResponse("el archivo de respuesta creado esta vacio");

							String rawContent = Files.readString(path, StandardCharsets.UTF_8);
							JsonNode root = objectMapper.readTree(sanitizeJson(rawContent));
							log(logPath, "JSON leido correctamente: " + filePath);

							if (jsonResponseHasErrors(root, filePath)) {
								log(logPath, "JSON con errores en campo errors: " + filePath);
								continue;
							}

							String steamId = root.path("parameters").path("team").asText();
							long teamId = Integer.toUnsignedLong(Integer.parseInt(steamId));

							// Primera vez que vemos un teamId válido: cargamos todos los jugadores existentes del equipo de una sola vez
							if (existingByIndexId == null) {
								existingByIndexId = new HashMap<>();
								for (Player p : pdClient.getPlayersByTeamId(teamId)) {
									if (p.getIndexId() != null) existingByIndexId.put(p.getIndexId(), p);
								}
							}

							JsonNode responseNode = root.path("response");
							for (JsonNode node : responseNode) {
								String playerName = node.path("player").path("name").asText("desconocido");
								try {
									JsonNode playerNode = node.path("player");
									System.out.println(playerName);

									long indexId = playerNode.path("id").asLong();
									Player player = existingByIndexId.getOrDefault(indexId, new Player());

									player.setIndexId(indexId);
									player.setTeam(teamId);
									player.setFirstname(playerNode.path("firstname").asText());
									player.setLastname(playerNode.path("lastname").asText());
									player.setFullname(playerNode.path("name").asText());
									player.setAge(playerNode.path("age").asInt());
									player.setInjured(playerNode.path("injured").asBoolean());

									String height = playerNode.path("height").asText();
									if (height != null && !height.equals("null")) {
										try {
											if (height.endsWith("cm")) height = height.substring(0, height.length() - 3);
											player.setHeight(Integer.parseInt(height.trim()));
										} catch (NumberFormatException e) {
											log(logPath, "No se pudo interpretar altura del jugador " + playerName + ": '" + height + "'");
										}
									}
									String weight = playerNode.path("weight").asText();
									if (weight != null && !weight.equals("null")) {
										try {
											if (weight.endsWith("kg")) weight = weight.substring(0, weight.length() - 3);
											player.setWeight(Integer.parseInt(weight.trim()));
										} catch (NumberFormatException e) {
											log(logPath, "No se pudo interpretar peso del jugador " + playerName + ": '" + weight + "'");
										}
									}

									// Lookup en el mapa pre-cargado en lugar de una query a la BBDD por jugador
									String nationality = playerNode.path("nationality").asText();
									Integer countryId = countryMap.get(nationality);
									if (countryId != null) player.setNacionalidad(countryId);

									JsonNode birthNode = playerNode.path("birth");
									String birthString = birthNode.path("date").asText();
									if (birthString != null && !birthString.equals("null") && !birthString.isBlank()) {
										try {
											player.setBirth(LocalDate.parse(birthString, formatter));
										} catch (Exception e) {
											log(logPath, "No se pudo interpretar fecha de nacimiento del jugador " + playerName + ": '" + birthString + "'");
										}
									}
									player.setLastUpdated(new Timestamp(System.currentTimeMillis()));

									// Descarga de imagen enviada al pool de hilos para ejecutarse en paralelo
									String photoUrl = playerNode.path("photo").asText(null);
									final byte[] fallback = standardImage;
									CompletableFuture<Void> imgFuture = CompletableFuture.runAsync(() -> {
										try {
											byte[] imageBytes = null;
											String contentType = null;
											if (photoUrl != null && !photoUrl.isBlank()) {
												ImageResult img = downloadImageWithType(photoUrl);
												if (img != null) {
													imageBytes = img.bytes();
													contentType = img.contentType();
												}
											}
											if (imageBytes == null || imageBytes.length == 0) {
												imageBytes = fallback;
												contentType = standardCt;
											}
											if (imageBytes != null && imageBytes.length > 0) {
												player.setPhoto(imageBytes);
												player.setPhotoContentType(contentType);
												player.setPhotoUpdatedAt(LocalDateTime.now());
											}
										} catch (Exception e) {
											log(logPath, "Error procesando foto del jugador " + playerName + ": " + e.getMessage());
										}
									}, imagePool);
									imageFutures.add(imgFuture);
									toSave.add(player);

								} catch (Exception e) {
									log(logPath, "ERROR al procesar jugador " + playerName + ": " + e.getMessage());
									System.err.println("ERROR al procesar jugador " + playerName + ": " + e.getMessage());
								}
							}
						} catch (Exception e) {
							log(logPath, "ERROR procesando archivo " + filePath + ": " + e.getMessage());
							System.err.println("Error procesando archivo " + filePath + ": " + e.getMessage());
						}
					}

					// Esperar a que terminen todas las descargas de imágenes del directorio antes de guardar
					if (!imageFutures.isEmpty()) {
						CompletableFuture.allOf(imageFutures.toArray(new CompletableFuture[0])).join();
					}

					// Guardar en lote todos los jugadores del equipo (1 operación en lugar de N)
					if (!toSave.isEmpty()) {
						try {
							pdClient.saveAllIndexedPlayers(toSave);
							log(logPath, "Guardados " + toSave.size() + " jugadores del directorio " + directory.getFileName());
							System.out.println("Guardados en batch: " + toSave.size() + " jugadores de " + directory.getFileName());
						} catch (Exception e) {
							log(logPath, "ERROR en batch save del directorio " + directory.getFileName() + ": " + e.getMessage());
							System.err.println("ERROR en batch save: " + e.getMessage());
						}
					}

					// Eliminar archivos procesados y directorio
					for (Path path : fileList) {
						try {
							Files.deleteIfExists(path);
							System.out.println("Archivo eliminado: " + path);
						} catch (Exception ex) {
							System.err.println("No se pudo eliminar el archivo: " + path);
						}
					}
					try (Stream<Path> remaining = Files.list(directory)) {
						if (remaining.findAny().isEmpty()) {
							Files.delete(directory);
							System.out.println("Directorio eliminado: " + directory);
						}
					} catch (Exception e) {
						System.err.println("No se pudo eliminar el directorio: " + directory);
					}
				}
			} finally {
				imagePool.shutdown();
			}
		}
		response.setCODE(Constants.CODE_OK);
		response.setDescription("OK");
		return response;
	}

	public boolean jsonResponseHasErrors(JsonNode root, String path) {
		JsonNode errorsNode = root.path("errors");
		if (!errorsNode.isMissingNode() && errorsNode.isArray() && errorsNode.size() > 0) {
			System.out.println("no se ha podido almacenar correctamente en BBDD los datos de " + path);
			return true;
		}
		return false;
	}

	private String sanitizeJson(String content) {
		return content
			.replace("    ", "")
			.replace("\n    ", "")
			.replace("\n", "")
			.replace("Türkiye", "Turkey");
	}

	private void log(Path logPath, String message) {
		String line = LocalDateTime.now().format(LOG_TS) + " " + message + System.lineSeparator();
		try {
			Files.writeString(logPath, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (Exception e) {
			System.err.println("No se pudo escribir en el log: " + e.getMessage());
		}
	}

	private ImageResult downloadImageWithType(String url) throws Exception {
		HttpRequest req = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.GET()
				.build();

		HttpResponse<byte[]> res = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());

		if (res.statusCode() != 200) return null;

		String contentType = res.headers().firstValue("Content-Type").orElse("image/png");

		long contentLength = res.headers().firstValue("Content-Length")
				.map(Long::parseLong).orElse(-1L);
		if (contentLength > 0 && contentLength > MAX_BYTES) return null;

		byte[] body = res.body();
		if (body != null && body.length > MAX_BYTES) return null;

		return new ImageResult(body != null ? body : new byte[0], contentType);
	}
}
