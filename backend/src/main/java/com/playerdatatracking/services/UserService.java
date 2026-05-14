package com.playerdatatracking.services;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.playerdatatracking.common.crypto.AESCrypto;
import com.playerdatatracking.entities.user.AppUser;
import com.playerdatatracking.repositories.user.AppUserRepository;

import jakarta.transaction.Transactional;

@Service
public class UserService {

    private final AppUserRepository repo;
    private final PasswordEncoder encoder;
    private final AESCrypto aesCrypto;

    public UserService(AppUserRepository repo, PasswordEncoder encoder, AESCrypto aesCrypto) {
        this.repo = repo;
        this.encoder = encoder;
        this.aesCrypto = aesCrypto;
    }

    @Transactional
    public AppUser register(String username, String rawPassword, String role) throws Exception {
        if (repo.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("El usuario ya existe");
        }
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(rawPassword));
        u.setRole(role != null && !role.isBlank() ? role : "user");
        try {
            u.setPasswordEncrypted(aesCrypto.encrypt(rawPassword));
        } catch (Exception e) {
            // Si falla el cifrado AES, dejamos el campo vacío pero no bloqueamos el registro
            u.setPasswordEncrypted(null);
        }
        return repo.save(u);
    }

    @Transactional
    public AppUser register(String username, String rawPassword) throws Exception {
        return register(username, rawPassword, "user");
    }

    public Map<String, Object> authenticate(String username, String rawPassword) {
        Optional<AppUser> opt = repo.findByUsername(username);
        if (opt.isEmpty()) return null;

        AppUser u = opt.get();
        if (!encoder.matches(rawPassword, u.getPasswordHash())) return null;

        String role = (u.getRole() != null && !u.getRole().isBlank()) ? u.getRole() : "user";
        return Map.of("id", u.getId(), "username", u.getUsername(), "roles", List.of(role));
    }

    public List<AppUser> getAllUsers() {
        return repo.findAll();
    }
}
