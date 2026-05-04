package com.playerdatatracking.clients;

import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.playerdatatracking.exceptions.apikeys.ApiFootballRequestException;
import com.playerdatatracking.exceptions.file.NotCreatedJsonFileResponse;
import com.playerdatatracking.entities.keys.Keys;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;


public class ApiFootballClient {
	 private static final HttpClient HTTP = HttpClient.newHttpClient();
	 
	 public void getLeaguesInfo(String apikey) throws Exception {
		 
		 HashMap<String, String> headers = new HashMap<>();
		 headers.put("x-rapidapi-key", apikey);
		 headers.put("x-rapidapi-host", "v3.football.api-sports.io");
		 
		 apiFootballClientCall("GET", "https://v3.football.api-sports.io/leagues", apikey, headers, null, "src/main/resources/json/apiFotball/leagues/leagues.json", "src/main/resources/json/apiFotball/leagues");
		 
	 }

	 public String getClubs(HashMap<String, String> queryParams, String apikey, String leagueName) throws Exception{
		 HashMap<String, String> headers = new HashMap<>();
		 headers.put("x-rapidapi-key", apikey);
		 headers.put("x-rapidapi-host", "v3.football.api-sports.io");
		 String jsonFilePath = "src/main/resources/json/apiFotball/leagues/" + leagueName +".json";
		 apiFootballClientCall("GET", "https://v3.football.api-sports.io/teams", apikey, headers, queryParams, "src/main/resources/json/apiFotball/leagues/" + leagueName +".json", "src/main/resources/json/apiFotball/leagues");
		 return jsonFilePath;
	 }
	 
	 public HttpResponse<String> getTransfer(String apiSportsKey, Long indexId) throws Exception{
		String url = "https://v3.football.api-sports.io/transfers?player=" + indexId;
		HttpRequest req = HttpRequest.newBuilder(URI.create(url))
		            .GET()
		            .header("Accept", "application/json")
		            .header("x-apisports-key", apiSportsKey)
		            .build();
		
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new ApiFootballRequestException("[" + resp.statusCode()+ "] API error para indexId="+ indexId);
        }
        return resp;
		 
	 }
	 
	 public String getPlayersPaged(HashMap<String, String> queryParams, String apikey, String teamName, String page) throws Exception{
		 //suponemos que queryParams, de inicio, trae consigo el valor de la página 1 como
		 HashMap<String, String> headers = new HashMap<>();
		 headers.put("x-rapidapi-key", apikey);
			 
		 headers.put("x-rapidapi-host", "v3.football.api-sports.io");
		 String jsonFilePath = "src/main/resources/json/apiFotball/players/" + teamName + "/" + teamName + "_" + page + ".json";
		 apiFootballClientCall("GET", "https://v3.football.api-sports.io/players", apikey, headers, queryParams, "src/main/resources/json/apiFotball/players/" + teamName + "/" + teamName + "_" + page + ".json", "src/main/resources/json/apiFotball/players/" + teamName);
		 return "src/main/resources/json/apiFotball/players/" + teamName + "/" + teamName + "_" + page + ".json";
	 } 
	 
	 public void getClubsPaged(HashMap<String, String> queryParams, String apikey, String leagueName, String page) throws Exception{
		 HashMap<String, String> headers = new HashMap<>();
		 headers.put("x-rapidapi-key", apikey);
		 headers.put("x-rapidapi-host", "v3.football.api-sports.io");
		 queryParams.put("page", page);
		 
		 apiFootballClientCall("GET", "https://v3.football.api-sports.io/teams", apikey, headers, queryParams, "src/main/resources/json/apiFotball/leagues/" + leagueName +".json", "src/main/resources/json/apiFotball/leagues");
		 
	 }
	 
	 public void getCountriesInfo(String apikey) throws Exception {
		 	 
		 HashMap<String, String> headers = new HashMap<>();
		 headers.put("x-rapidapi-key", apikey);
		 headers.put("x-rapidapi-host", "v3.football.api-sports.io");
		 
		 apiFootballClientCall("GET", "https://v3.football.api-sports.io/countries", apikey, headers, null, "src/main/resources/json/apiFotball/countries/countries.json", "src/main/resources/json/apiFotball/countries");
	
	 }
	 
	 private void apiFootballClientCall(String method, String url, String apikey, HashMap<String, String> headers, HashMap<String, String> queryParams, String filePath, String sJsonDir) throws Exception {
		 	AsyncHttpClient client = new DefaultAsyncHttpClient();
		 	
		 	if (queryParams != null && !queryParams.isEmpty()) {
		        StringBuilder urlBuilder = new StringBuilder(url);
		        urlBuilder.append("?");
		        queryParams.forEach((key, value) -> {
		            if (value != null && !value.isEmpty()) {
		                urlBuilder.append(key).append("=").append(value).append("&");
		            }
		        });
		        // Eliminar el último '&' si existe
		        url = urlBuilder.substring(0, urlBuilder.length() - 1);
		    }
		 	
	        var requestBuilder = client.prepare(method, url);
	        headers.forEach((key, value) -> requestBuilder.setHeader(key, value));
	        CompletableFuture<Response> futureResponse = requestBuilder.execute().toCompletableFuture();
	        futureResponse.thenAccept(response -> {

	            String responseBody = response.getResponseBody();
	            File jsonDir = new File(sJsonDir);
	            if (!jsonDir.exists()) {
	                jsonDir.mkdirs(); // Crea la carpeta si no existe
	            }

	            ObjectMapper mapper = new ObjectMapper();
	            mapper.enable(SerializationFeature.INDENT_OUTPUT);
	            try {
	                Object json = mapper.readValue(responseBody, Object.class);
	                ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
	                try (FileWriter fileWriter = new FileWriter(filePath)) {
	                    fileWriter.write(writer.writeValueAsString(json));
	                    System.out.println("Respuesta guardada en el archivo: " + filePath);
	                }
	            } catch (IOException e) {
	                e.printStackTrace();
	            }
	        }).join();
	        checkForErrors(filePath);
	        client.close();
		 
	 }
	 
    public String getLiveFixturesRaw(String apikey) throws Exception {
        String url = "https://v3.football.api-sports.io/fixtures?live=all";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .header("Accept", "application/json")
                .header("x-apisports-key", apikey)
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200)
            throw new ApiFootballRequestException("[" + resp.statusCode() + "] API error fetching live fixtures");
        return resp.body();
    }

    public String getFixtureEventsRaw(String apikey, Long fixtureId) throws Exception {
        String url = "https://v3.football.api-sports.io/fixtures/events?fixture=" + fixtureId;
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .header("Accept", "application/json")
                .header("x-apisports-key", apikey)
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200)
            throw new ApiFootballRequestException("[" + resp.statusCode() + "] API error fetching events for fixture=" + fixtureId);
        return resp.body();
    }

    public String getFixturesByLeagueAndSeasonRaw(String apikey, Long leagueId, Integer season) throws Exception {
        String url = "https://v3.football.api-sports.io/fixtures?league=" + leagueId + "&season=" + season;
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .header("Accept", "application/json")
                .header("x-apisports-key", apikey)
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200)
            throw new ApiFootballRequestException("[" + resp.statusCode() + "] API error fetching fixtures for league=" + leagueId + " season=" + season);
        return resp.body();
    }

    public String getFixtureByIdRaw(String apikey, Long fixtureId) throws Exception {
        String url = "https://v3.football.api-sports.io/fixtures?id=" + fixtureId;
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .header("Accept", "application/json")
                .header("x-apisports-key", apikey)
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200)
            throw new ApiFootballRequestException("[" + resp.statusCode() + "] API error fetching fixture id=" + fixtureId);
        return resp.body();
    }

    public void checkForErrors(String filePath) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new NotCreatedJsonFileResponse("json file was not created while doing the call");
        }

        try (InputStream inputStream = new FileInputStream(file)) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(inputStream);

            JsonNode errorsNode = rootNode.path("errors");
            if (errorsNode.isArray() && errorsNode.size() > 0) {
                throw new ApiFootballRequestException("Errors were found making apiFootball call: " + errorsNode.toString());
            }
        } catch (Exception e) {
            throw e;
        }
    }
    
    
}