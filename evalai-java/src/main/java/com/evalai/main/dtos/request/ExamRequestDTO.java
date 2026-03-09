package com.evalai.main.dtos.request;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExamRequestDTO{
	@NotBlank(message = "Exam title is required")
	private String title;
	
	@NotBlank(message = "Subject Id is required")
	private String subjectId;
	
	@NotNull(message = "Exam date is required")
	private LocalDateTime examDate;
	
	@NotNull(message = "academic year required")
	private String academicYear;
	
	@NotNull(message = "total marks required")
	private Integer totalMarks;
	
	@NotNull(message = "duration of exam required")
	private Integer duration;
	
	@NotNull(message = "grievance deadline required")
	private LocalDateTime grievanceDeadline;
}