package com.fattorestreet.sec_api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The service exposes read-only market data and has no authenticated routes.
 *
 * <p>It used to verify Django-issued SimpleJWT HS256 tokens for {@code /admin/**}, which meant
 * sharing Django's {@code SECRET_KEY} with this service. Those routes are gone -- every capability
 * they offered now runs as a scheduled Fargate task selected by {@code APP_RUN_MODE} -- so the JWT
 * decoder, the authorities converter and the shared secret went with them. Spring Boot starts fine
 * with no {@code SECRET_KEY} in its environment.
 *
 * <p>What remains is the filter chain itself: CORS from the servlet-level configuration, CSRF
 * disabled and sessions stateless (both correct for a token-free read-only API consumed by a
 * separate origin). The explicit {@code permitAll} matchers are kept rather than collapsed into
 * {@code anyRequest().permitAll()} so the public surface stays written down in one place.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/quarters",
                                "/company-fact-sheet",
                                "/prices",
                                "/dividends",
                                "/splits",
                                "/filing-summaries",
                                "/index-members",
                                "/indexes",
                                "/iwb-reference-holdings",
                                "/fred-data"
                        ).permitAll()
                        .anyRequest().permitAll());
        return http.build();
    }
}
