package com.evalai.main.dtos.response;

import java.time.LocalDateTime;

import com.evalai.main.enums.GrievanceStatus;
import com.evalai.main.enums.GrievanceType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StudentGrievanceResponseDTO {
	private String grievanceId;
    private String subQuestionLabel;
    private GrievanceType grievanceType;
    private GrievanceStatus status;
    private String studentReason;
    private Float requestedMarks;
    private Float awardedMarks;
    private Float aiMarks;           
    private Float finalMarks;
    private String reviewerComment;
    private LocalDateTime raisedAt;
    private LocalDateTime resolvedAt;
}
