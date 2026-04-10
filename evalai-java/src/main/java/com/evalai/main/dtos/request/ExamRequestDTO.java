package com.evalai.main.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * FIX: subjectId (String) → subjectIds (List<String>)
 * because one exam can have many subjects.
 */
@Getter
@Setter
public class ExamRequestDTO {

    @NotBlank(message = "Exam title is required")
    private String title;

    /**
     * FIX: was "private String subjectId" — single subject.
     * Now accepts a list so admin can link multiple subjects to one exam.
     */
    @NotEmpty(message = "At least one subject ID is required")
    private List<String> subjectIds;

    @NotNull(message = "Exam date is required")
    private LocalDateTime examDate;

    @NotNull(message = "Academic year required")
    private String academicYear;

    @NotNull(message = "Total marks required")
    private Integer totalMarks;

    @NotNull(message = "Duration of exam required")
    private Integer duration;

    @NotNull(message = "Grievance deadline required")
    private LocalDateTime grievanceDeadline;
}