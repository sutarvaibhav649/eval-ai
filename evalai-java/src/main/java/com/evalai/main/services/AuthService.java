package com.evalai.main.services;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.evalai.main.entities.UserEntity;
import com.evalai.main.repositories.UserRepository;

/**
 * Service class for handling authentication-related operations such as user
 * registration and login. This class interacts with the UserRepository to
 * perform database operations and uses PasswordEncoder for secure password
 *
 * @author Vaibhav Sutar
 * @version 1.0
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Function to register user with hashed password
     *
     * @param userEntity the user entity containing registration details such as
     * name, email, password, department, and role
     * @return the saved UserEntity with hashed password if registration is
     * successful
     * @throws RuntimeException if a user with the same email already exists in
     * the database
     */
    public UserEntity registerUser(UserEntity userEntity) {
        if (userRepository.findByEmail(userEntity.getEmail()).isPresent()) {
            throw new RuntimeException("USER_ALREADY_EXISTS");
        }
        userEntity.setPassword(passwordEncoder.encode(userEntity.getPassword()));
        return userRepository.save(userEntity);
    }

    /**
     * Function to login user by verifying hashed password
     *
     * @param email the email of the user attempting to log in
     * @param password the plaintext password provided by the user for login
     *
     * @return the UserEntity if the email exists and the password matches the
     * hashed password stored in the database, otherwise returns null
     */
    public UserEntity loginUser(String email, String password) {
        // Find user by email
        return userRepository.findByEmail(email)
                .filter(user -> passwordEncoder.matches(password, user.getPassword()))
                .orElse(null); // Returns user if password matches, otherwise null
    }
}
