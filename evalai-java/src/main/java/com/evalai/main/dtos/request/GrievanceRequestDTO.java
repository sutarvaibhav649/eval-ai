package com.evalai.main.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GrievanceRequestDTO {
	@NotBlank(message = "Result ID is required")
    private String resultId;

    @NotBlank(message = "Reason is required")
    private String reason;

    @NotNull(message = "Requested marks is required")
    private Float requestedMarks;
}
