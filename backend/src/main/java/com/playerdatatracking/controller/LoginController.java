package com.playerdatatracking.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/auth")
public class LoginController {

	private boolean validate(String username, String password) {
		// Sustituye por tu lógica/BD
		return "demo".equals(username) && "secret".equals(password);
	}

	@PostMapping("/login")
	public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpServletRequest request) {
		String user = body.get("username");
		String pass = body.get("password");
		
		if (!validate(user, pass)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("ok", false));
		}
		
		HttpSession session = request.getSession(true);
	    session.setAttribute("USER", Map.of("username", user, "roles", List.of("USER")));

		return ResponseEntity.ok(Map.of("ok", true));
	}

	@PostMapping("/logout")
	public ResponseEntity<?> logout(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null)
			session.invalidate();
		return ResponseEntity.ok(Map.of("ok", true));
	}

	@GetMapping("/me")
	public ResponseEntity<?> me(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null)
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		Object user = session.getAttribute("USER");
		if (user == null)
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		return ResponseEntity.ok(user);
	}
}

