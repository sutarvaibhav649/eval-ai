package com.evalai.main.services;

import com.evalai.main.dtos.request.RegisterRequestDTO;
import com.evalai.main.entities.SubjectEntity;
import com.evalai.main.enums.UserRole;
import com.evalai.main.repositories.SubjectRepository;
import org.apache.coyote.BadRequestException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.evalai.main.entities.UserEntity;
import com.evalai.main.repositories.UserRepository;

import java.util.ArrayList;
import java.util.List;

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
    private final SubjectRepository subjectRepository;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,SubjectRepository subjectRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.subjectRepository = subjectRepository;
    }

    /**
     * Function to register user with hashed password
     *
     * @param userEntity the user entity containing registration details such as
     * name, email, password, department, and role
     * @return the saved UserEntity with hashed password if registration is
     * successful
     * @throws BadRequestException 
     * @throws RuntimeException if a user with the same email already exists in
     * the database
     */
    public UserEntity registerUser(RegisterRequestDTO dto) throws BadRequestException {

        // Step 1 — Check duplicate email
        if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new BadRequestException("USER_ALREADY_EXISTS");
        }

        // Step 2 — Build base user
        UserEntity user = new UserEntity();
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setDepartment(dto.getDepartment());
        user.setRole(dto.getRole());

        // Step 3 — Role-specific fields
        if (dto.getRole() == UserRole.STUDENT) {
            validateStudentFields(dto);

            user.setRollNo(dto.getRollNo());
            user.setYear(dto.getYear());
            user.setSemester(dto.getSemester());

            // Enroll in subjects with semester validation
            if (dto.getSubjectIds() != null && !dto.getSubjectIds().isEmpty()) {
                List<SubjectEntity> subjects = resolveAndValidateSubjects(
                        dto.getSubjectIds(), dto.getSemester(), "Student"
                );
                user.setSubjects(subjects);
            }

        } else if (dto.getRole() == UserRole.FACULTY) {
            validateFacultyFields(dto);

            user.setDesignation(dto.getDesignation());

            // Assign teaching subjects (no semester restriction for faculty)
            if (dto.getTeachingSubjectIds() != null && !dto.getTeachingSubjectIds().isEmpty()) {
                List<SubjectEntity> subjects = new ArrayList<>();
                for (String subjectId : dto.getTeachingSubjectIds()) {
                    SubjectEntity subject = subjectRepository.findById(subjectId)
                            .orElseThrow(() -> new BadRequestException(
                                    "SUBJECT_NOT_FOUND: " + subjectId
                            ));
                    subjects.add(subject);
                }
                user.setSubjects(subjects);
            }
        }

        return userRepository.save(user);
    }

    /*----------------------------------------------------------
                        PRIVATE HELPERS
    ----------------------------------------------------------*/
    private void validateStudentFields(RegisterRequestDTO dto) throws BadRequestException {
        if (dto.getRollNo() == null || dto.getRollNo().isBlank()) {
            throw new BadRequestException("ROLL_NO_REQUIRED_FOR_STUDENT");
        }
        if (dto.getYear() == null || dto.getYear().isBlank()) {
            throw new BadRequestException("YEAR_REQUIRED_FOR_STUDENT");
        }
        if (dto.getSemester() == null) {
            throw new BadRequestException("SEMESTER_REQUIRED_FOR_STUDENT");
        }
    }

    private void validateFacultyFields(RegisterRequestDTO dto) throws BadRequestException {
        if (dto.getDesignation() == null || dto.getDesignation().isBlank()) {
            throw new BadRequestException("DESIGNATION_REQUIRED_FOR_FACULTY");
        }
    }

    private List<SubjectEntity> resolveAndValidateSubjects(
            List<String> subjectIds,
            Integer studentSemester,
            String context
    ) throws BadRequestException {
        List<SubjectEntity> subjects = new ArrayList<>();
        for (String subjectId : subjectIds) {
            SubjectEntity subject = subjectRepository.findById(subjectId)
                    .orElseThrow(() -> new BadRequestException(
                            "SUBJECT_NOT_FOUND: " + subjectId
                    ));

            // Semester validation
            if (!subject.getSemester().equals(studentSemester)) {
                throw new BadRequestException(
                        "SEMESTER_MISMATCH: Subject " + subject.getCode() +
                                " is for semester " + subject.getSemester() +
                                " but student is in semester " + studentSemester
                );
            }

            subjects.add(subject);
        }
        return subjects;
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
