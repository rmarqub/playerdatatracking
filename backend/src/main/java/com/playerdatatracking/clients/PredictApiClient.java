package com.playerdatatracking.clients;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerdatatracking.responses.MatchPrediction;

@Service
public class PredictApiClient {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${predict.api.url:http://localhost:8001}")
    private String apiUrl;

    public MatchPrediction predict(Long fixtureId) throws Exception {
        String body = "{\"fixture_id\":" + fixtureId + "}";

        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl + "/predict"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() == 404) {
            throw new IllegalArgumentException("Fixture " + fixtureId + " no encontrado en la base de datos del modelo");
        }
        if (response.statusCode() != 200) {
            throw new RuntimeException("Error del servicio de predicción [HTTP " + response.statusCode() + "]: " + response.body());
        }

        return MAPPER.readValue(response.body(), MatchPrediction.class);
    }
}
