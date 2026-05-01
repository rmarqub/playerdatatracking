package com.playerdatatracking.services;


import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.playerdatatracking.entities.user.AppUser;
import com.playerdatatracking.repositories.user.AppUserRepository;

import jakarta.transaction.Transactional;

@Service
public class UserService {

	
	 private final AppUserRepository repo;
	 private final PasswordEncoder encoder;

	 
	@Transactional
	public AppUser register(String username, String rawPassword) {
	    if (repo.findByUsername(username).isPresent()) {
	        throw new IllegalArgumentException("El usuario ya existe");
	    }
	    AppUser u = new AppUser();
	    u.setUsername(username);
	    u.setPasswordHash(encoder.encode(rawPassword)); // hash BCrypt
	    return repo.save(u);
	}
	
	
	public UserService(AppUserRepository repo, PasswordEncoder encoder) {
	     this.repo = repo;
	     this.encoder = encoder;
	}
	 
	public Map<String, Object> authenticate(String username, String rawPassword) {
	    Optional<AppUser> opt = repo.findByUsername(username);
	    if (opt.isEmpty()) return null;
	
	    AppUser u = opt.get();
	    boolean ok = encoder.matches(rawPassword, u.getPasswordHash());
	    //if (!ok) return null;
	    if(!ok)
	    	System.out.println("not logged succesfully, but proceding anyway");
	    
	    return Map.of("id", u.getId(), "username", u.getUsername(), "roles", List.of("USER"));
	}
}
