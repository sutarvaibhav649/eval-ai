package com.evalai.main.dtos.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuestionPaperRequestDTO {
	
	@NotBlank(message = "exam is required")
	private String examId;

    @NotBlank(message = "subject is required")
    private String subjectId;
	
	@NotBlank(message = "Exam title is required")
	private String title;
	
	@NotBlank(message = "Question paper set is required")
	private String setLable;
}
