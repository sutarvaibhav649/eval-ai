package com.evalai.main.controllers;

import com.evalai.main.dtos.ApiResponse;
import org.apache.coyote.BadRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.evalai.main.dtos.request.LoginRequestDTO;
import com.evalai.main.dtos.request.RegisterRequestDTO;
import com.evalai.main.dtos.response.LoginResponseDTO;
import com.evalai.main.dtos.response.RegisterResponseDTO;
import com.evalai.main.entities.UserEntity;
import com.evalai.main.enums.UserRole;
import com.evalai.main.services.AuthService;
import com.evalai.main.utils.JwtUtils;

import jakarta.validation.Valid;

import java.util.Map;

/**
 * Controller class for handling authentication-related endpoints such as user
 * registration and login. This class defines RESTful API endpoints that
 * interact with the AuthService to perform the necessary business logic for
 * authentication operations. It also uses the UserRepository to check for
 * existing users and the JwtUtils to
 *
 * @author Vaibhav Sutar
 * @version 1.0
 */
/**
 * Handles user registration and login endpoints. All DB logic delegated to
 * AuthService — no repository access here.
 *
 * @author Vaibhav Sutar
 * @version 2.0
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtUtils jwtUtils;

    public AuthController(AuthService authService, JwtUtils jwtUtils) {
        this.authService = authService;
        this.jwtUtils = jwtUtils;
    }

    /**
     * Registers a new user in the system.
     *
     * Flow: 1. Validate request body via @Valid 2. Delegate to AuthService —
     * handles duplicate check + password hashing 3. Build and return
     * RegisterResponseDTO with saved user details
     *
     * @param dto validated request body with name, email,
     * password, department, role
     * @return 201 CREATED with user details on success 409 CONFLICT if email
     * already registered 500 INTERNAL_SERVER_ERROR on unexpected failure
     * @throws BadRequestException 
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequestDTO dto) throws BadRequestException {
        try {
            UserEntity saved = authService.registerUser(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    ApiResponse.builder()
                            .success(true)
                            .message("User registered successfully")
                            .data(Map.of(
                                    "userId", saved.getId(),
                                    "name", saved.getName(),
                                    "email", saved.getEmail(),
                                    "role", saved.getRole(),
                                    "department", saved.getDepartment()
                            ))
                            .timestamp(System.currentTimeMillis())
                            .build()
            );
        } catch (BadRequestException e) {
            throw e;
        }
    }

    /**
     * Authenticates an existing user and returns a JWT access token.
     *
     * Flow: 1. Delegate credential validation to AuthService 2. On success —
     * generate JWT containing email, userId, role 3. Return token + user info
     * so React can route to correct dashboard
     *
     * Security note: AuthService returns null for BOTH "user not found" and
     * "wrong password" intentionally — this prevents user enumeration attacks
     * where an attacker could discover which emails are registered by observing
     * different error messages.
     *
     * @param loginRequestDTO request body with email and password
     * @return 200 OK with JWT token and user info on success 401 UNAUTHORIZED
     * for any credential failure (wrong email or password) 500
     * INTERNAL_SERVER_ERROR on unexpected failure
     */
    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@Valid @RequestBody LoginRequestDTO loginRequestDTO) {
        try {
            // Step 1 — Validate credentials via AuthService
            // Returns null for both "user not found" and "wrong password" — intentional
            UserEntity userInDB = authService.loginUser(
                    loginRequestDTO.getEmail(),
                    loginRequestDTO.getPassword()
            );

            // Step 2 — Null means either email not found or password mismatch
            // Return 401 for both cases — never reveal which one failed
            if (userInDB == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Invalid email or password");
            }

            // Step 3 — Generate JWT token with email, userId, role embedded
            String accessToken = jwtUtils.generateToken(
                    userInDB.getEmail(),
                    userInDB.getId(),
                    userInDB.getRole().toString()
            );

            // Step 4 — Build response DTO with token + user info
            // React uses role to decide which dashboard to render after login
            LoginResponseDTO response = new LoginResponseDTO();
            response.setAccessToken(accessToken);
            response.setUserId(userInDB.getId());
            response.setName(userInDB.getName());
            response.setEmail(userInDB.getEmail());
            response.setRole(userInDB.getRole());
            response.setDepartment(userInDB.getDepartment());

            return ResponseEntity.status(HttpStatus.OK).body(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Login failed: " + e.getMessage());
        }
    }
}
