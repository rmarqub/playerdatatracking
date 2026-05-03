package com.playerdatatracking.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.playerdatatracking.common.Methods;
import com.playerdatatracking.common.crypto.AESCrypto;
import com.playerdatatracking.operations.apikeys.KeysManagement;
import com.playerdatatracking.requests.GenericRequest;
import com.playerdatatracking.responses.GenericResponse;

@RestController
public class ApiKeyController {

    @Autowired
    private KeysManagement operationKeys;
    @Autowired
    private AESCrypto operationCrypto;

    @GetMapping("/secretkey")
    public GenericResponse getSecretKey() throws Exception {
        GenericResponse response = new GenericResponse();
        try {
            response = operationCrypto.getNewSecretKey();
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/apiKey")
    public GenericResponse storeApiKey(@RequestBody GenericRequest request) {
        GenericResponse response = new GenericResponse();
        try {
            response = operationKeys.storeKey(request.getNewKey(), request.getMail(), request.getPlan(), request.getIdService());
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @GetMapping("/apiKey")
    public GenericResponse getApiKey(@RequestBody GenericRequest request) {
        GenericResponse response = new GenericResponse();
        try {
            response = operationKeys.getKeyByKey(request.getApiKey());
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }

    @GetMapping("/myapiKeys")
    public GenericResponse getApiKeysbyMail(@RequestBody GenericRequest request) {
        GenericResponse response = new GenericResponse();
        try {
            response = operationKeys.getKeyByMail(request.getMail());
        } catch (Exception e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
        }
        return response;
    }
}
