package com.evalai.main.dtos.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CallbackPayload {

    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("context")
    private ContextPayload context;

    @JsonProperty("student")
    private StudentPayload student;

    @JsonProperty("status")
    private String status;

    @JsonProperty("ocr_engine_used")
    private String ocrEngineUsed;

    @JsonProperty("total_pages_processed")
    private Integer totalPagesProcessed;

    @JsonProperty("failed_count")
    private Integer failedCount;

    @JsonProperty("completed_count")
    private Integer completedCount;

    @JsonProperty("extracted_answers")
    private List<ExtractedAnswerPayload> extractedAnswers;

    @Getter @Setter
    public static class ContextPayload {
        @JsonProperty("exam_id")
        private String examId;
        @JsonProperty("exam_name")
        private String examName;
        @JsonProperty("course_id")
        private String courseId;
        @JsonProperty("course_name")
        private String courseName;
        @JsonProperty("subject_code")
        private String subjectCode;
        @JsonProperty("subject_name")
        private String subjectName;
        @JsonProperty("academic_year")
        private String academicYear;
        @JsonProperty("question_paper_id")
        private String questionPaperId;
        @JsonProperty("question_paper_set")
        private String questionPaperSet;
    }

    @Getter @Setter
    public static class StudentPayload {
        @JsonProperty("student_id")
        private String studentId;
        @JsonProperty("answer_sheet_id")
        private String answerSheetId;
    }

    @Getter @Setter
    public static class ExtractedAnswerPayload {
        @JsonProperty("sub_question_id")
        private String subQuestionId;
        @JsonProperty("sub_question_label")
        private String subQuestionLabel;
        @JsonProperty("question_number")
        private Integer questionNumber;
        @JsonProperty("subject_code")
        private String subjectCode;
        @JsonProperty("status")
        private String status;
        @JsonProperty("failure_reason")
        private String failureReason;
        @JsonProperty("extracted_text")
        private String extractedText;
        @JsonProperty("cleaned_text")
        private String cleanedText;
        @JsonProperty("embedding")
        private List<Float> embedding;
        @JsonProperty("ocr_confidence")
        private Float ocrConfidence;
        @JsonProperty("similarity_score")
        private Float similarityScore;
        @JsonProperty("ai_marks")
        private Float aiMarks;
    }
}