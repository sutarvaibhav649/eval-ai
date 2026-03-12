package com.evalai.main.dtos.response;

import java.util.List;

import com.evalai.main.enums.EvaluationStatus;
import com.evalai.main.enums.ResultStatus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StudentResultResponseDTO {
	private String examTitle;
	private String subjectName;
	private String academicYear;
	private EvaluationStatus evaluationStatus;
	private Float totalMarks;
	private Float obtainedMarks;
	private List<SubQuestionResultDTO> result;
	
	@Getter
	@Setter
	public static class SubQuestionResultDTO{
		private String resultId;
		private String subQuestionLable;
		private String subQuestionText;
		private Float maxMarks;       
        private Float finalMarks;    
        private Boolean isOverridden; 
        private ResultStatus status;
	}
}
