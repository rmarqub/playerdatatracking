package com.playerdatatracking.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.playerdatatracking.common.crypto.AESCrypto;
import com.playerdatatracking.common.crypto.CryptoRSAService;
import com.playerdatatracking.common.crypto.RsaKeyProvider;
import com.playerdatatracking.entities.user.AppUser;
import com.playerdatatracking.responses.UserInfo;
import com.playerdatatracking.services.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/auth")
public class LoginController {

    private final RsaKeyProvider keys;
    private final UserService users;
    private final CryptoRSAService crypto;
    private final AESCrypto aesCrypto;

    public LoginController(RsaKeyProvider keys, UserService users, CryptoRSAService crypto, AESCrypto aesCrypto) {
        this.keys = keys;
        this.users = users;
        this.crypto = crypto;
        this.aesCrypto = aesCrypto;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpServletRequest request) {
        try {
            String username = body.get("username");
            String cipherB64 = body.get("pwd");

            String rawPassword = crypto.decryptPasswordB64(cipherB64);

            var claims = users.authenticate(username, rawPassword);
            if (claims == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("ok", false));
            }

            HttpSession session = request.getSession(true);
            session.setAttribute("USER", claims);

            var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
            var auth = new UsernamePasswordAuthenticationToken(claims.get("username"), null, authorities);

            SecurityContext sc = SecurityContextHolder.createEmptyContext();
            sc.setAuthentication(auth);
            SecurityContextHolder.setContext(sc);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, sc);

            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("ok", false, "error", "crypto_error"));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        try {
            String username = body.get("username");
            String cipherB64 = body.get("pwd");
            String role = body.get("role");

            String rawPassword = crypto.decryptPasswordB64(cipherB64);
            if (username == null || username.isBlank() || rawPassword == null || rawPassword.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "invalid_input"));
            }

            if (rawPassword.length() < 8) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "weak_password"));
            }

            users.register(username, rawPassword, role);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("ok", true));

        } catch (IllegalArgumentException dup) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("ok", false, "error", "user_exists"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("ok", false, "error", "crypto_error"));
        }
    }

    @GetMapping("/users")
    public ResponseEntity<?> listUsers() {
        try {
            List<AppUser> all = users.getAllUsers();
            List<UserInfo> result = all.stream().map(u -> {
                String plainPwd = null;
                if (u.getPasswordEncrypted() != null) {
                    try {
                        plainPwd = aesCrypto.decrypt(u.getPasswordEncrypted());
                    } catch (Exception ignored) {}
                }
                return new UserInfo(u.getId(), u.getUsername(), u.getRole(), plainPwd);
            }).collect(Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("ok", false, "error", e.getMessage()));
        }
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

    @GetMapping("/pubkey")
    public ResponseEntity<?> pubkey() {
        return ResponseEntity.ok(Map.of(
                "kid", keys.getKid(),
                "alg", "RSA-OAEP-256",
                "pem", keys.exportPublicKeyPEM()
        ));
    }
}
