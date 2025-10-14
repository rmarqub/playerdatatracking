package com.playerdatatracking.repositories.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

import com.playerdatatracking.entities.user.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long>{
	
	Optional<AppUser> findByUsername(String username);

}
