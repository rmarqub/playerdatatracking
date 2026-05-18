package com.playerdatatracking.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Redirige los 404 de rutas Angular (sin extensión) a index.html,
 * permitiendo que el frontend SPA maneje su propio routing.
 */
@Controller
public class SpaController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request) {
        Object statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (statusCode != null && Integer.parseInt(statusCode.toString()) == HttpStatus.NOT_FOUND.value()) {
            String uri = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
            if (uri != null && !uri.contains(".")) {
                return "forward:/index.html";
            }
        }
        return "forward:/index.html";
    }
}
