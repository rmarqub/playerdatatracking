package com.playerdatatracking.operations.IndelxalData;

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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.sql.Timestamp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
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
	String directoryPath = "src/main/resources/json/apiFotball/players/";
	String leaguesPath = "src/main/resources/json/apiFotball/leagues/";
	String excludedLeague = "leagues.json";
	private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
	private static final long MAX_BYTES = 5L * 1024 * 1024;
	private static final String DEFAULT_CT = "image/png";
	private static final DateTimeFormatter LOG_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private GenericResponse<Player> response = new GenericResponse();
	private Methods methods;
	DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

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

			List<Path> directories = Files.list(Paths.get(directoryPath)).filter(Files::isDirectory).collect(Collectors.toList());
			if (directories == null || directories.isEmpty())
				throw new NotCreatedJsonFileResponse("No hay archivos de jugadores disponibles para realizar la carga de datos");

			ObjectMapper objectMapper = new ObjectMapper();

			for (Path directory : directories) {
				List<Path> fileList;
				try (Stream<Path> files = Files.list(directory)) {
					fileList = files.filter(Files::isRegularFile).collect(Collectors.toList());
				}
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
						JsonNode responseNode = root.path("response");
						for (JsonNode node : responseNode) {
							String playerName = node.path("player").path("name").asText("desconocido");
							try {
								JsonNode playerNode = node.path("player");
								System.out.println(playerName);

								long indexId = playerNode.path("id").asLong();
								long teamId = Integer.toUnsignedLong(Integer.parseInt(steamId));

								List<Player> existing = pdClient.getPlayerByIndexIdAndTeam(teamId, indexId);
								boolean isUpdate = existing != null && !existing.isEmpty();
								Player player = isUpdate ? existing.get(0) : new Player();

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

								String photoUrl = playerNode.path("photo").asText(null);
								try {
									byte[] imageBytes = null;
									String contentType = null;
									if (photoUrl != null && !photoUrl.isBlank()) {
										imageBytes = downloadImage(photoUrl);
										contentType = lastContentType != null ? lastContentType : "image/png";
									}
									if (imageBytes == null || imageBytes.length == 0) {
										try (InputStream in = getClass().getResourceAsStream("/images/standard-pic.jpg")) {
											if (in != null) {
												imageBytes = in.readAllBytes();
												contentType = "image/jpeg";
											} else {
												log(logPath, "No se encontro la imagen estandar para el jugador " + playerName);
												System.err.println("⚠️ No se encontró la imagen estándar en resources/images/standard-pic.jpg");
											}
										}
									}
									if (imageBytes != null && imageBytes.length > 0) {
										player.setPhoto(imageBytes);
										player.setPhotoContentType(contentType);
										player.setPhotoUpdatedAt(LocalDateTime.now());
									}
								} catch (Exception e) {
									log(logPath, "Error procesando foto del jugador " + playerName + ": " + e.getMessage());
									System.err.println("⚠️ Error procesando la foto: " + e.getMessage());
								}

								JsonNode birthNode = playerNode.path("birth");
								String birthString = birthNode.path("date").asText();
								if (birthString != null && !birthString.equals("null")) {
									try {
										LocalDate date = LocalDate.parse(birthString, formatter);
										player.setBirth(date);
									} catch (Exception e) {
										log(logPath, "No se pudo interpretar fecha de nacimiento del jugador " + playerName + ": '" + birthString + "'");
									}
								}
								Pais p = pdClient.findCountry(playerNode.path("nationality").asText());
								if (p != null) player.setNacionalidad(p.getId());
								player.setLastUpdated(new Timestamp(System.currentTimeMillis()));

								Player saved = pdClient.saveIndexedPlayer(player);
								String action = isUpdate ? "updated" : "saved";
								log(logPath, "Player " + action + ": " + saved.getId() + ", " + saved.getFullname());
								System.out.println("Player " + action + ": " + saved.getId() + ", " + saved.getFullname());
							} catch (Exception e) {
								log(logPath, "ERROR al procesar jugador " + playerName + ": " + e.getMessage());
								System.err.println("ERROR al procesar jugador " + playerName + ": " + e.getMessage());
							}
						}
					} catch (Exception e) {
						log(logPath, "ERROR procesando archivo " + filePath + ": " + e.getMessage());
						System.err.println("Error procesando archivo " + filePath + ": " + e.getMessage());
					} finally {
						try {
							Files.deleteIfExists(path);
							System.out.println("Archivo eliminado: " + filePath);
						} catch (Exception ex) {
							System.err.println("No se pudo eliminar el archivo: " + filePath);
						}
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

	private String lastContentType = null;

	private byte[] downloadImage(String url) throws Exception {
		HttpRequest req = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.GET()
				.build();

		HttpResponse<byte[]> res = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());

		if (res.statusCode() != 200) return null;

		lastContentType = res.headers().firstValue("Content-Type").orElse(null);

		long contentLength = res.headers().firstValue("Content-Length")
				.map(Long::parseLong).orElse(-1L);
		if (contentLength > 0 && contentLength > MAX_BYTES) return null;

		byte[] body = res.body();
		if (body != null && body.length > MAX_BYTES) return null;

		return body;
	}
}
