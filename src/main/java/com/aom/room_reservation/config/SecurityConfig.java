package com.aom.room_reservation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Read endpoints (GET /api/**) are public; anything that creates or cancels requires HTTP Basic.
 * <p>
 * The user comes from spring.security.user.* (in-memory, overridable with APP_USER/APP_PASSWORD).
 * Stateless: no HTTP session, credentials are sent on every request, so CSRF protection
 * (which protects cookie-based sessions) is not needed and is disabled.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint entryPoint = new ProblemDetailAuthenticationEntryPoint();

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/**").permitAll()
                        .requestMatchers("/h2-console/**").permitAll() // dev only, in-memory DB
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))
                // the H2 console UI is rendered inside frames of the same origin
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .build();
    }
}
