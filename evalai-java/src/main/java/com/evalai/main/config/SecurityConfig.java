package com.evalai.main.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.evalai.main.utils.JwtFilter;

/**
 * This class is responsible for configuring the security settings of the
 * application using Spring Security. It defines how HTTP requests are secured,
 * which endpoints are public, and how JWT authentication is integrated into the
 * security filter chain. It uses the JwtFilter to validate JWT tokens for
 * incoming requests and sets up the security context accordingly. The
 * configuration ensures that only authenticated users can access protected
 * endpoints while allowing public access to authentication-related endpoints.
 *
 * @author Vaibhav Sutar
 * @version 1.0
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    /**
     * This method configures the security filter chain for the application. It
     * disables CSRF protection (since we're using JWTs), sets the session
     * management to stateless, and defines which endpoints are publicly
     * accessible and which require authentication. It also adds the JwtFilter
     * to the filter chain to ensure that JWT tokens are validated for incoming
     * requests before they reach the controllers.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Disable CSRF (standard for Stateless JWT APIs)
                .csrf(csrf -> csrf.disable())
                // 2. Set Session Management to Stateless
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 3. Define URL permissions
                .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**").permitAll() //login and response endpoints should be public
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/faculty/**").hasRole("FACULTY")
                .requestMatchers("/student/**").hasRole("STUDENT")
                .requestMatchers("/pipeline/callback").permitAll()
                .anyRequest().authenticated() // Everything else needs a token
                );

        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
