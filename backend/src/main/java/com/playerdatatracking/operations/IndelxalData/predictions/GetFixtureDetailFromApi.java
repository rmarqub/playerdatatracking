package com.playerdatatracking.operations.IndelxalData.predictions;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerdatatracking.clients.ApiFootballClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.common.Methods;
import com.playerdatatracking.entities.keys.Keys;
import com.playerdatatracking.exceptions.apikeys.ApiFootballRequestException;
import com.playerdatatracking.exceptions.apikeys.ApiKeyManagementException;
import com.playerdatatracking.exceptions.operations.PlayerInputException;
import com.playerdatatracking.operations.apikeys.KeysManagement;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.GenericResponse;

@Component
public class GetFixtureDetailFromApi {

    @Autowired
    private KeysManagement keyMethods;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public GenericResponse<Object> ejecutar(GenericRequest request) throws Exception {
        if (request.getId() == null)
            throw new PlayerInputException("Se requiere el id del fixture");

        GenericResponse<Object> response = new GenericResponse<>();
        ApiFootballClient client = new ApiFootballClient();

        Keys apiKey = keyMethods.nextKey();
        if (apiKey == null)
            throw new ApiKeyManagementException("no hay almacenada ninguna key valida");
        if (!keyMethods.checkReadiness(apiKey))
            throw new ApiKeyManagementException("la key disponible no está lista para usarse");

        String jsonBody = client.getFixtureByIdRaw(apiKey.getValor(), request.getId());
        keyMethods.useKey(apiKey);
        Methods.sleep(180);

        JsonNode root = objectMapper.readTree(jsonBody);

        JsonNode errors = root.path("errors");
        if (errors.isObject() && errors.size() > 0)
            throw new ApiFootballRequestException("API devolvió errores: " + errors.toString());

        JsonNode responseArray = root.path("response");
        if (!responseArray.isArray() || responseArray.size() == 0)
            throw new PlayerInputException("No se encontró ningún fixture con id=" + request.getId());

        Map<String, Object> fixtureDetail = objectMapper.convertValue(
                responseArray.get(0), new TypeReference<Map<String, Object>>() {});

        response.setCODE(Constants.CODE_OK);
        response.setDescription("OK");
        response.setEntity(fixtureDetail);
        return response;
    }
}
