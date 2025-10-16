package com.playerdatatracking.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/players/**")
            .allowedOrigins("http://localhost:4200") // tu front
            .allowedMethods("GET")
            .allowCredentials(false); // al ser público, no hacen falta cookies
  }
}

