package com.playerdatatracking.operations.IndelxalData;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.text.SimpleDateFormat;
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

import org.springframework.core.env.Environment;

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

public class UpdatePlayersData {

	
	private PlayerDataClient pdClient;
	private ApiFootballClient restClient;
	private KeysManagement keyMethods = new KeysManagement();
	private Environment env;
	String directoryPath = "src/main/resources/json/apiFotball/players/";
	String leaguesPath = "src/main/resources/json/apiFotball/leagues/";
	String excludedLeague = "leagues.json";
	private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
	private static final long MAX_BYTES = 5L * 1024 * 1024; // 5 MB
	private static final String DEFAULT_CT = "image/png";
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
		keyMethods.setEnv(env);
		keyMethods.setPdClient(pdClient);
		methods = new Methods();
		List<Club> updatedClubs = new ArrayList<Club>();
		try {
			if(request.getRestUpdate()!=null && request.getRestUpdate().equals("true")) {
				String actualSeason = pdClient.getParam(Constants.ACTUAL_APF_SEASON).getValue();
				List<Torneo> studiedLeagues = pdClient.getStudiedLeagues();
				List<Club> clubList = pdClient.getAllClubs();
				if(clubList.size()>0) {
					Keys apiKey = keyMethods.nextKey();
					if (apiKey==null)
						throw new ApiKeyManagementException("no hay almacenada ninguna key valida");
					for (Club club : clubList) {
						List<ClubInLeague> cilList = pdClient.findCILsByClub(club.getId());
						if (cilList!=null && cilList.size()>0) {
							for(ClubInLeague cil: cilList) {
								Torneo auxLeague = pdClient.getTorneoById(cil.getTorneoId());
								if(auxLeague != null && studiedLeagues.contains(auxLeague) && !updatedClubs.contains(club)) {
									updatedClubs.add(club);
									HashMap<String, String> queryParams = new HashMap<>();
									queryParams.put("season", actualSeason);
									queryParams.put("page", "1");
									int actualPage = 1;
									queryParams.put("team", club.getId().toString());
									String responsePath = "";
									if (keyMethods.checkReadiness(apiKey)) {
										responsePath = restClient.getPlayersPaged(queryParams,apiKey.getValor(),club.getNombre(),"1");
										methods.checkGoodPlayersCall(responsePath, queryParams,apiKey.getValor(),club.getNombre(),"1");
										System.out.println("Club: " + club.getNombre() + ", Page: " + actualPage);
										keyMethods.useKey(apiKey);
									}
									else
										throw new ApiKeyManagementException("error al intentar usar una key no disponible");
									int totalofPages = methods.getTotalOfPagesResponse(responsePath);
									while(actualPage<totalofPages) {
										actualPage++;
										queryParams.put("page", Integer.toString(actualPage));
										if (keyMethods.checkReadiness(apiKey)) {
											responsePath = restClient.getPlayersPaged(queryParams,apiKey.getValor(),club.getNombre(),Integer.toString(actualPage));
											methods.checkGoodPlayersCall(responsePath, queryParams,apiKey.getValor(),club.getNombre(),Integer.toString(actualPage));
											System.out.println("Club: " + club.getNombre() + ", Page: " + actualPage + "/" + totalofPages + " stored");
											keyMethods.useKey(apiKey);
										}
										else {
											throw new ApiKeyManagementException("error al intentar usar una key no disponible");
										}
									}
								}
							}
						}
					}
				}
			} 
			if(request.getUpdate()!=null && request.getUpdate().equals("true")) {
				try {
					String actualSeason = pdClient.getParam(Constants.ACTUAL_APF_SEASON).getValue();
					List<Path> directories = Files.list(Paths.get(directoryPath)).filter(Files::isDirectory).collect(Collectors.toList());
					if (directories==null || !(directories.size()>0))
						throw new NotCreatedJsonFileResponse("No hay archivos de jugadores disponibles para realizar la carga de datos");
					pdClient.deleteAllPlayers();
		            for (Path directory : directories) {
		            	try (Stream<Path> files = Files.list(directory)) {
		                    List<Path> fileList = files.filter(Files::isRegularFile).collect(Collectors.toList());
		                    for (Path path : fileList) {
		                    	String filePath = path.toString();
		                        System.out.println("  Archivo: " + filePath);
					            File file = new File(filePath);
						        if (!file.exists())
						            throw new NotCreatedJsonFileResponse("error al crear un json de respuesta, el archivo no ha sido creado o no se ha guardado correctamente");
						        
						        if (file.length() == 0) 
						            throw new NotFilledJsonFileResponse("el archivo de respuesta creado esta vacio");
						        FileReader fileReader = new FileReader(file);
						        int ch;
					            if ((ch = fileReader.read()) == -1) {
					            	fileReader.close();
					            	throw new NotFilledJsonFileResponse("el archivo de respuesta creado esta vacio");
					            }
					            fileReader.close();
						        
						        ObjectMapper objectMapper = new ObjectMapper();
						        JsonNode root = objectMapper.readTree(file);
						        if(!jsonResponseHasErrors(root, file.getPath())) {
						        	JsonNode responseParameters = root.path("parameters");
						        	String steamId = responseParameters.path("team").asText();
						        	String jsonSeason = responseParameters.path("season").asText();
						        	if(!actualSeason.equals(jsonSeason))
						        		continue;
						        		
						        	JsonNode responseNode = root.path("response");
							        for (JsonNode node : responseNode) {
							        	
							        	Player newPlayer = new Player();
							        	JsonNode playerNode = node.path("player");
							        	System.out.println((playerNode.path("name").asText()));
							        	
							        	newPlayer.setIndexId(playerNode.path("id").asLong());
							        	newPlayer.setTeam(Integer.toUnsignedLong(Integer.parseInt(steamId)));
							        	newPlayer.setFirstname(playerNode.path("firstname").asText());
							        	newPlayer.setLastname(playerNode.path("lastname").asText());
							        	newPlayer.setFullname(playerNode.path("name").asText());
							        	newPlayer.setAge(playerNode.path("age").asInt());
							        	newPlayer.setInjured(playerNode.path("injured").asBoolean());
							        	String height = playerNode.path("height").asText();
							        	if(height!=null && !height.equals("null")){
							        		if (height.endsWith("cm"))
							        			height = height.substring(0, height.length() - 3);
							        		newPlayer.setHeight(Integer.parseInt(height));
							        	}
							        	String weight = playerNode.path("weight").asText();
							        	if(weight!=null && !weight.equals("null")) {
							        		if (weight.endsWith("kg"))
							        			weight = weight.substring(0, weight.length() - 3);
							        		newPlayer.setWeight(Integer.parseInt(weight));
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
							        	                System.err.println("⚠️ No se encontró la imagen estándar en resources/images/standard-pic.jpg");
							        	            }
							        	        }
							        	    }

							        	    if (imageBytes != null && imageBytes.length > 0) {
							        	        newPlayer.setPhoto(imageBytes);
							        	        newPlayer.setPhotoContentType(contentType);
							        	        newPlayer.setPhotoUpdatedAt(LocalDateTime.now());
							        	    }

							        	} catch (Exception e) {
							        	    System.err.println("⚠️ Error procesando la foto: " + e.getMessage());
							        	}

							        	
							        	
							        	JsonNode birthNode = playerNode.path("birth");
							        	String birthString = birthNode.path("date").asText();
							        	if(birthString!=null && !birthString.equals("null")) {
							        		LocalDate date = LocalDate.parse(birthString, formatter);
							        		newPlayer.setBirth(date);
							        	}
							        	Pais p = pdClient.findCountry(playerNode.path("nationality").asText());
							        	if (p!=null)
							        		newPlayer.setNacionalidad(p.getId());
							        	Timestamp ts = new Timestamp(System.currentTimeMillis());
							        	newPlayer.setLastUpdated(ts);
							        	
							        	//find duplicates
							        	List<Player> duppedPlayers = pdClient.getPlayerByIndexIdAndTeam(newPlayer.getTeam(), newPlayer.getIndexId());
							        	if (duppedPlayers!=null) {
							        		if(!duppedPlayers.isEmpty()) {
							        			System.out.println("Player: " + newPlayer.getFullname() + " already stored");
							        			continue;
							        		}
							        			
							        	}
							        	Player savedPlayer = pdClient.saveIndexedPlayer(newPlayer);
							        	System.out.println("Player: " + savedPlayer.getId() + ", " + savedPlayer.getFullname() + " saved.");
							        }
						        }
						        
		                    }
		                }
		            }


		        } catch (Exception e) {
		            throw e;
		        }
				
			}
			response.setCODE(Constants.CODE_OK);
			response.setDescription("OK");
			return response;
		} catch(Exception e) {
			throw e;
		}
	}
	
	public boolean jsonResponseHasErrors(JsonNode root, String path) {
		JsonNode errorsNode = root.path("errors");
		if (!errorsNode.isMissingNode() && errorsNode.isArray() && errorsNode.size()>0) {
			System.out.println("no se ha podido almacenar correctamente en BBDD los datos de " + path);
			return true;
		}
		return false;
	}
	
	private String lastContentType = null;

	private byte[] downloadImage(String url) throws Exception {
	    HttpRequest req = HttpRequest.newBuilder()
	            .uri(URI.create(url))
	            .GET()
	            .build();

	    HttpResponse<byte[]> res = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());

	    if (res.statusCode() != 200) return null;

	    // Guardamos content-type para usarlo al setear la entidad
	    lastContentType = res.headers().firstValue("Content-Type").orElse(null);

	    // (Opcional) validar tamaño si el servidor lo expone
	    long contentLength = res.headers().firstValue("Content-Length")
	            .map(Long::parseLong).orElse(-1L);
	    if (contentLength > 0 && contentLength > MAX_BYTES) return null;

	    byte[] body = res.body();
	    if (body != null && body.length > MAX_BYTES) return null;

	    return body;
	}
}
