package com.playerdatatracking.operations.IndelxalData;

import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerdatatracking.clients.ApiFootballClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.entities.keys.Keys;
import com.playerdatatracking.exceptions.apikeys.ApiFootballRequestException;
import com.playerdatatracking.exceptions.apikeys.ApiKeyManagementException;
import com.playerdatatracking.operations.apikeys.KeysManagement;
import com.playerdatatracking.responses.GenericResponse;

@Component
public class GetLiveFixturesFromApi {

    @Autowired
    private KeysManagement keyMethods;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public GenericResponse<Object> ejecutar() throws Exception {
        GenericResponse<Object> response = new GenericResponse<>();
        ApiFootballClient client = new ApiFootballClient();

        Keys apiKey = keyMethods.nextKey();
        if (apiKey == null)
            throw new ApiKeyManagementException("no hay almacenada ninguna key valida");
        if (!keyMethods.checkReadiness(apiKey))
            throw new ApiKeyManagementException("la key disponible no está lista para usarse");

        String jsonBody = client.getLiveFixturesRaw(apiKey.getValor());
        keyMethods.useKey(apiKey);

        JsonNode root = objectMapper.readTree(jsonBody);

        JsonNode errors = root.path("errors");
        if (errors.isObject() && errors.size() > 0)
            throw new ApiFootballRequestException("API devolvió errores: " + errors.toString());

        JsonNode responseArray = root.path("response");
        List<Object> fixtures = responseArray.isArray() && responseArray.size() > 0
                ? objectMapper.convertValue(responseArray, new TypeReference<List<Object>>() {})
                : Collections.emptyList();

        response.setCODE(Constants.CODE_OK);
        response.setDescription("OK");
        response.setEntityList(fixtures);
        return response;
    }
}
