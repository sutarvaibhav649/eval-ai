package com.evalai.main.dtos.response;

import com.evalai.main.enums.UserRole;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO class for handling user registration responses. This class contains
 * fields that represent the information of a newly registered user, such as id,
 * name, email, department , role, and active status. It uses Lombok annotations
 * to generate getters and setters for ease of use. This DTO is used in the
 * AuthController to send back the details of the newly registered user to the
 * client after a successful registration attempt.
 *
 * @author Vaibhav Sutar
 * @version 1.0
 */
@Getter
@Setter
public class RegisterResponseDTO {

    private String id;
    private String name;
    private String email;
    private String department;
    private UserRole role;
    private Boolean isActive;
}
