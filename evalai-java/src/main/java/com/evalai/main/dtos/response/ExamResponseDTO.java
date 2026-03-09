package com.evalai.main.dtos.response;

import java.time.LocalDateTime;

import com.evalai.main.enums.ExamStatus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExamResponseDTO {
	private String id;
	private String title;
    private String subjectName;
    private String subjectCode;
    private String academicYear;
    private LocalDateTime examDate;
    private Integer totalMarks;
    private Integer durationMinutes;
    private ExamStatus status;
    private LocalDateTime grievanceDeadline;
    private LocalDateTime createdAt;
}
