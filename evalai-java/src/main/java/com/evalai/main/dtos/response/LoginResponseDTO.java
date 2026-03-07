package com.evalai.main.dtos.response;

import com.evalai.main.enums.UserRole;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO class for handling user login responses. This class contains a field for
 * the access token that is generated upon successful login. It uses Lombok
 * annotations to generate getters and setters for ease of use. This DTO is used
 * in the AuthController to send the access token back to the client after a
 * successful login attempt.
 *
 * @author Vaibhav Sutar
 * @version 1.0
 */
@Getter
@Setter
public class LoginResponseDTO {

    private String accessToken;
    private String userId;
    private String name;
    private String email;
    private UserRole role;
    private String department;
}
