package com.evalai.main.dtos.response;

import java.time.LocalDateTime;

import com.evalai.main.enums.QuestionPaperStatus;

import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class QuestionPaperResponseDTO {
	
	private String questionPaperId;
    private String title;
    private String setLabel;
    private String examId;
    private String examTitle;
    private QuestionPaperStatus status;
    private LocalDateTime createdAt;

}
