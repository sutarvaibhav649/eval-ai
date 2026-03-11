package com.evalai.main.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubQuestionRequestDTO {
	@NotBlank(message = "Question id required")
	private String questionId;
	
	@NotBlank(message = "sub question lable required")
	private String subQuestionLabel;
	
	@NotBlank(message = "question text required")
	private String questionText;
	
	@NotNull(message = "marks for the question is required")
	private Float marks;
}
