package com.playerdatatracking.configuration;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
	  
	  
    http
    	.csrf(csrf -> csrf.disable())
    	.cors(cors -> {})
    	.authorizeHttpRequests(auth -> auth
    			.requestMatchers("/auth/**", "/public/**").permitAll()
    			.anyRequest().authenticated()
      )

      .sessionManagement(sm -> sm
        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) // crea sesión cuando haga falta
      );
    
    http.sessionManagement(sm -> sm
    		  .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
    		  .maximumSessions(3)              		// SESIONES MAXIMAS
    		  .maxSessionsPreventsLogin(false)    	// si llega al límite, invalida la más antigua
    		).addFilterBefore(new SessionUserAuthFilter(), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);


    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    // IMPORTANTE: no usar "*", debe ser el origen exacto del front
    config.setAllowedOrigins(List.of("http://localhost:4200"));
    config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS","PATCH"));
    config.setAllowedHeaders(List.of("Content-Type","Authorization","X-Requested-With"));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}

