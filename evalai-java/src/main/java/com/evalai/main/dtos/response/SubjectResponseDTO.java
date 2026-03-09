package com.evalai.main.dtos.response;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubjectResponseDTO {
	private String subjectId;
    private String name;
    private String code;
    private String department;
    private Integer semester;
    private LocalDateTime createdAt;
}
