package com.nas_backend.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;


@Configuration
public class SecurityConfig {

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http.csrf(csrf -> csrf.disable())
                    .cors(cors -> cors.configure(http))
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
                return http.build();
        }

        @Bean
        public CorsFilter corsFilter() {
                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                CorsConfiguration config = new CorsConfiguration();
                
                // Allow specific origins
                config.setAllowedOrigins(List.of("http://localhost:3000", "http://192.168.1.213:3000")); 
                
                config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
                config.setAllowCredentials(true); // Allow cookies and credentials
                
                source.registerCorsConfiguration("/**", config);
                return new CorsFilter(source);
        }
}