package com.nas_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/performLogin", "/css/**", "/js/**", "/images/**", "/error").permitAll() // Do not require authentication for these endpoints
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login") // Login page endpoint
                        .loginProcessingUrl("/performLogin") // Spring Security POST endpoint
                        .permitAll())
                .logout(logout -> logout.permitAll());
        return http.build();
    }
}