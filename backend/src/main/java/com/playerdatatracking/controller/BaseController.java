package com.playerdatatracking.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

public abstract class BaseController {

    protected Long currentUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        Object user = session.getAttribute("USER");
        if (!(user instanceof Map<?,?> map) || map.get("id") == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return ((Number) map.get("id")).longValue();
    }
}
