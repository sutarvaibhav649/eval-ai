package com.evalai.main.dtos.response;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * FIX: Added setDepartment() — was missing, causing AdminController
 * to call setName(getDepartment()) as a workaround (which overwrote the name).
 */
@Getter
@Setter
public class SubjectResponseDTO {
    private String subjectId;
    private String name;
    private String code;
    private String department;   // FIX: was missing — caused name/department mix-up in controller
    private Integer semester;
    private LocalDateTime createdAt;
}
