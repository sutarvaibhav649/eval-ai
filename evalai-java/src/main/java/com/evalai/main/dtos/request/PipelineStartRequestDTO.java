package com.evalai.main.dtos.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PipelineStartRequestDTO {
	@NotBlank(message = "Exam ID is required")
    private String examId;

    @NotBlank(message = "Subject ID is required")
    private String subjectId;
}
