package com.evalai.main.utils;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;

/**
 * Utility class for handling JWT token generation and validation. This class
 * provides methods to generate JWT tokens with user information (email, role,
 * userId) and to validate and extract information from incoming JWT tokens. It
 * uses the jjwt library to handle JWT operations and relies on a secret key and
 *
 * @author Vaibhav Sutar
 * @version 1.0
 */
@Component
public class JwtUtils {

    /**
     * inject jwt secret
     */
    @Value("${app.jwt.secret}")
    private String JWTSECRET;

    /**
     * inject jwt expiration
     */
    @Value("${app.jwt.expiration}")
    private long JWTEXPIRATION;

    private SecretKey key;

    /**
     * This generate the bytes after all the values injected after @Value
     */
    @PostConstruct
    public void init() {
        if (JWTSECRET == null || JWTSECRET.getBytes().length < 32) {
            throw new IllegalStateException(
                    "JWT secret must be at least 32 characters long. Check app.jwt.secret in application.properties"
            );
        }
        this.key = Keys.hmacShaKeyFor(JWTSECRET.getBytes());
    }

    /**
     * Utility to generate token using
     *
     * @param email user's email to be set as subject in the token
     * @param userId user's ID to be included as a claim in the token
     * @param role user's role to be included as a claim in the token
     * @return a signed JWT token containing the user's email, role, and userId
     * with an expiration time defined by JWTEXPIRATION. The token is signed
     * using the secret key configured in the application properties.
     */
    public String generateToken(String email, String userId, String role) {
        return Jwts
                .builder()
                .subject(email)
                .claim("role", role)
                .claim("userId", userId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + JWTEXPIRATION))
                .signWith(key)
                .compact();

    }

    /**
     * Utility to extract the email from the token
     *
     * @param token the JWT token from which to extract the email
     * @return the email (subject) contained in the token if valid, otherwise
     * throws a RuntimeException with details about the validation failure. This
     * method relies on the secret key to verify the token's integrity and will
     * catch exceptions related to expired, tampered, or malformed tokens,
     * providing a clear error message for debugging.
     *
     */
    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Utility to extract the role
     *
     * @param token the JWT token from which to extract the role
     * @return the role claim contained in the token if valid, otherwise throws
     * a RuntimeException with details about the validation failure. This method
     * relies on the secret key to verify the token's integrity and will catch
     * exceptions related to expired, tampered, or malformed tokens, providing a
     * clear error message for debugging.
     */
    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    /**
     * Utility to extract the userId
     *
     * @param token the JWT token from which to extract the userId
     * @return the userId claim contained in the token if valid, otherwise
     * throws a RuntimeException with details about the validation failure. This
     * method relies on the secret key to verify the token
     *
     */
    public String extractUserId(String token) {
        return extractAllClaims(token).get("userId", String.class);
    }

    /**
     * Check the token is valid
     *
     * @param token the JWT token to validate
     * @return true if the token is valid (not expired, correctly signed, and
     * well-formed), false otherwise. This method uses the secret key to verify
     */
    public boolean isValidToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Utility to extract all claims (private helper to avoid code duplication)
     *
     * @param token the JWT token from which to extract claims
     * @return the Claims object containing all claims from the token if valid,
     * otherwise throws a RuntimeException with details about the validation
     * failure. This method relies on the secret key to verify the token's
     * integrity and will catch exceptions related to expired, tampered, or
     * malformed tokens, providing a clear error message for debugging.
     */
    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            // This catches expired, tampered, or malformed tokens
            throw new RuntimeException("JWT Validation failed: " + e.getMessage());
        }
    }
}
