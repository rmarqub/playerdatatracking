package com.playerdatatracking.clients;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerdatatracking.responses.PlayerMarketValue;

@Service
public class ValuationApiClient {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${valuation.api.url:http://localhost:8002}")
    private String apiUrl;

    public PlayerMarketValue getPlayerValue(Long indexId) throws Exception {
        String url = apiUrl + "/player-value/" + indexId;

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = HTTP.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() == 404) {
            throw new IllegalArgumentException(
                    "Jugador con index_id=" + indexId + " no encontrado en la API de valoración");
        }
        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Error de la API de valoración [HTTP " + response.statusCode() + "]: " + response.body());
        }

        return MAPPER.readValue(response.body(), PlayerMarketValue.class);
    }
}
