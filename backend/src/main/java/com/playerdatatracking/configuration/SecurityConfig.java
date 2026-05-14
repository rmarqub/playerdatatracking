package com.playerdatatracking.configuration;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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

                // ── Admin-only: gestión de usuarios ──────────────────────────
                .requestMatchers(HttpMethod.GET,  "/auth/users").hasRole("ADMIN")

                // ── Admin-only: sincronización de datos ──────────────────────
                .requestMatchers(HttpMethod.POST, "/leagues").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/updateLeagues").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/countries").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/updateCountries").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/updateStudiedLeagues").hasRole("ADMIN")

                // ── Rutas públicas (usadas también por scripts externos) ──────
                .requestMatchers(HttpMethod.POST, "/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/updatePlayers").permitAll()
                .requestMatchers(HttpMethod.POST, "/ingestRawData").permitAll()
                .requestMatchers(HttpMethod.POST, "/ingestFixtureEvents").permitAll()
                .requestMatchers(HttpMethod.POST, "/ingestFixturePlayerStats").permitAll()
                .requestMatchers(HttpMethod.POST, "/ingestFixtureLineup").permitAll()
                .requestMatchers(HttpMethod.POST, "/playerAbsenceDays").permitAll()
                .requestMatchers(HttpMethod.POST, "/analysisHistory").permitAll()
                .requestMatchers(HttpMethod.POST, "/matchPrediction").permitAll()
                .requestMatchers(HttpMethod.POST, "/regenerateContextualAnalyses").permitAll()
                .requestMatchers(HttpMethod.POST, "/generatePlayerPercentiles").permitAll()
                .requestMatchers(HttpMethod.POST, "/getPlayerPercentiles").permitAll()
                .requestMatchers(HttpMethod.POST, "/ingestFixtureTeamStats").permitAll()
                .requestMatchers(HttpMethod.POST, "/generatePlayerPercentilesFrontend").permitAll()
                .requestMatchers(HttpMethod.POST, "/transformRawToStats").permitAll()
                .requestMatchers(HttpMethod.POST, "/ingestFixtures").permitAll()
                .requestMatchers(HttpMethod.POST, "/updateTransferedPlayers").permitAll()
                .requestMatchers(HttpMethod.POST, "/updatePlayerBySquads").permitAll()
                .requestMatchers(HttpMethod.POST, "/updateClubsInfo").permitAll()

                // ── Auth y rutas públicas generales ──────────────────────────
                .requestMatchers("/auth/**", "/public/**").permitAll()

                // ── Todo lo demás requiere autenticación ─────────────────────
                .anyRequest().authenticated()
            )
            .sessionManagement(sm -> sm
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .maximumSessions(3)
                .maxSessionsPreventsLogin(false)
            )
            .addFilterBefore(
                new SessionUserAuthFilter(),
                org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:4200"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-Requested-With"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
