package com.evalai.main.dtos.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ModelAnswerRequestDTO {
	
	@NotBlank(message = "Sub question id required")
	private String subQuestionId;
	
	@NotBlank(message = "Answer text is requires")
	private String answerText;
	
	private List<String> keyConcepts;
}
