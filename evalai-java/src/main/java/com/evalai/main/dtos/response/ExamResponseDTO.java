package com.evalai.main.dtos.response;

import com.evalai.main.enums.ExamStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * FIX: Returns List<SubjectInfo> instead of single subjectName/subjectCode
 * to reflect the new ManyToMany exam-subject relationship.
 */
@Getter
@Setter
public class ExamResponseDTO {

    private String id;
    private String title;

    /**
     * FIX: was separate subjectName + subjectCode strings.
     * Now a list of subject summaries matching the ManyToMany relation.
     */
    private List<SubjectInfo> subjects;

    private String academicYear;
    private LocalDateTime examDate;
    private Integer totalMarks;
    private Integer durationMinutes;
    private ExamStatus status;
    private LocalDateTime grievanceDeadline;
    private LocalDateTime createdAt;

    /**
     * Lightweight nested DTO — avoids exposing full SubjectEntity.
     */
    @Getter
    @Setter
    public static class SubjectInfo {
        private String subjectId;
        private String name;
        private String code;
        private String department;
        private Integer semester;
    }
}
