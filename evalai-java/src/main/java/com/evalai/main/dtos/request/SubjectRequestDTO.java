package com.evalai.main.dtos.request;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;


@Getter
@Setter
public class SubjectRequestDTO{

	@NotBlank(message="Subject name is required")
	private String name;

	@NotBlank(message="Subject code is required")
	private String code;

	@NotBlank(message="Department is required")
	private String department;

	@NotNull(message = "Semester is required")
	@Min(value = 1, message = "Semester must be at least 1")
    @Max(value = 8, message = "Semester cannot exceed 8")
	private int semester;
}