package com.evalai.main.utils;

import java.io.IOException;
import java.util.Collections;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * This filter intercepts incoming HTTP requests to validate JWT tokens. It
 * checks the Authorization header for a Bearer token, extracts the email and
 * role from the token, and if valid, sets the authentication in the Spring
 * Security context. This allows the application to identify the user making the
 * request and enforce security rules based on their role. If the token is
 * invalid or missing, the filter simply allows the request to proceed without
 * authentication, which will result in a 401 Unauthorized response for
 * protected endpoints.
 *
 * @author Vaibhav Sutar
 * @version 1.0
 */
@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;

    public JwtFilter(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        // step-1: Get the Authorization header from the request
        String authHeader = request.getHeader("Authorization");
        String token = null;
        String email = null;

        // step-2: Check if the header starts with "Bearer "
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);  // Extracts the actual token

            try {
                email = jwtUtils.extractEmail(token);  //extracts email from token
            } catch (Exception e) {
                logger.error("JWT validation failed: " + e.getMessage());
            }
        }

        // step-3: If we have an email and the user isn't already authenticated in this session
        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            if (jwtUtils.isValidToken(token)) {
                String role = jwtUtils.extractRole(token);
                //step-4: Create an Authentication object for Spring Security
                // We prefix the role with "ROLE_" because Spring Security expects it for hasRole()
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        email,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
                );

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // step-5: Final Step: Set the user in the Security Context
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        // Continue the filter chain
        filterChain.doFilter(request, response);
    }

}
