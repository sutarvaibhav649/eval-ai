package com.evalai.main.dtos.request;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO class for handling user login requests. This class contains fields for
 * the email and password that are required for a user to log in. It uses Lombok
 * annotations to generate getters and setters for ease of use. This DTO is used
 *
 * @author Vaibhav Sutar
 * @version 1.0
 */
@Getter
@Setter
public class LoginRequestDTO {

    private String email;
    private String password;
}
