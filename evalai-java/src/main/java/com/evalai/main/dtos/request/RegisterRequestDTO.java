package com.evalai.main.dtos.request;

import com.evalai.main.enums.UserRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO class for handling user registration requests. This class contains fields
 * that represent the necessary information for registering a new user, such as
 * name, email, password, department, and role. It uses Lombok annotations to
 * generate getters, setters, and constructors for ease of use. This DTO is used
 * in the AuthController to receive and process registration data from the
 * client.
 *
 * @author Vaibhav Sutar
 * @version 1.0
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequestDTO {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Department is required")
    private String department;

    private UserRole role;

}
