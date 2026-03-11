package com.evalai.main.dtos.request;

import com.evalai.main.enums.GrievanceStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GrievanceReviewRequest {
	@NotNull(message = "Awarded marks is required")
    private Float awardedMarks;

    @NotBlank(message = "Reviewer comment is required")
    private String reviewerComment;

    @NotNull(message = "Status is required")
    private GrievanceStatus status; // RESOLVED or REJECTED
}
