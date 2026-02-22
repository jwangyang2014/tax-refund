package com.intuit.taxrefund.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intuit.taxrefund.api.ApiError;
import com.intuit.taxrefund.auth.jwt.JwtAuthenticationFilter;
import com.intuit.taxrefund.observability.RequestCorrelationFilter;
import com.intuit.taxrefund.ratelimit.RateLimitFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.time.Instant;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        RequestCorrelationFilter requestCorrelationFilter,
        JwtAuthenticationFilter jwtAuthenticationFilter,
        RateLimitFilter rateLimitFilter,
        ObjectMapper objectMapper
    ) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())

            .exceptionHandling(eh -> eh.authenticationEntryPoint((req, res, ex) -> {
                writeApiError(objectMapper, res,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Unauthorized",
                    ex.getMessage() != null ? ex.getMessage() : "Unauthorized",
                    req.getRequestURI()
                );
            }))

            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health/**").permitAll()
                // For demo you might allow prometheus publicly; in real prod, secure it.
                .requestMatchers(HttpMethod.GET, "/actuator/prometheus").permitAll()
                .anyRequest().authenticated())

            // JWT first (sets SecurityContext)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            // Correlation after JWT (can read userId reliably)
            .addFilterAfter(requestCorrelationFilter, JwtAuthenticationFilter.class)
            // Rate limit AFTER JWT so it can use userId; before controllers
            .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    private static void writeApiError(
        ObjectMapper objectMapper,
        HttpServletResponse res,
        int status,
        String error,
        String message,
        String path
    ) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiError body = new ApiError(
            Instant.now(),
            status,
            error,
            message,
            path
        );

        objectMapper.writeValue(res.getOutputStream(), body);
    }
}