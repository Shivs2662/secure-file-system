package com.gehu.sfs.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain chain(HttpSecurity http) throws Exception {
        http
          // Session-cookie auth for a same-origin SPA. CSRF is disabled for simplicity (coursework scope); see README.
          .csrf(AbstractHttpConfigurer::disable)
          .headers(h -> h.frameOptions(f -> f.deny()))
          .authorizeHttpRequests(a -> a
              .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()
              .requestMatchers("/api/admin/**", "/api/audit-logs", "/api/audit-logs/**").hasRole("ADMIN")
              .requestMatchers("/api/**").authenticated()
              .anyRequest().permitAll())
          .exceptionHandling(e -> e
              .authenticationEntryPoint((req, res, ex) -> {
                  res.setStatus(HttpStatus.UNAUTHORIZED.value());
                  res.setContentType("application/json");
                  res.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"Please log in\"}");
              })
              .accessDeniedHandler((req, res, ex) -> {
                  res.setStatus(HttpStatus.FORBIDDEN.value());
                  res.setContentType("application/json");
                  res.getWriter().write("{\"error\":\"FORBIDDEN\",\"message\":\"You do not have access to this resource\"}");
              }))
          .logout(l -> l.disable());
        return http.build();
    }
}
