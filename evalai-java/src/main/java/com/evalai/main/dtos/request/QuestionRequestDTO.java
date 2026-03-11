package com.evalai.main.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuestionRequestDTO {
	
	@NotBlank(message = "Question paper id is required")
	private String questionPaperId;
	
	@NotNull(message = "Question number is required")
	private Integer questionNumber;
	
	@NotNull(message = "Total marks for the question is required")
	private Float totalMarks;
}
