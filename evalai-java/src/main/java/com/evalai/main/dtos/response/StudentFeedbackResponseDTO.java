package com.evalai.main.dtos.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StudentFeedbackResponseDTO {
	private String examTitle;
    private String subjectName;
    private List<SubQuestionFeedbackDTO> feedbacks;

    @Getter @Setter
    public static class SubQuestionFeedbackDTO {
        private String subQuestionLabel;
        private String strengths;
        private String weakness;
        private String suggestions;
        private String overallFeedback;
        private List<String> keyConceptsMissed;
    }
}
