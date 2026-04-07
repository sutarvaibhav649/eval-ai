package com.evalai.main.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.evalai.main.dtos.response.CallbackPayload;
import com.evalai.main.entities.AnswersheetEntity;
import com.evalai.main.entities.ExamEntity;
import com.evalai.main.entities.FeedbackEntity;
import com.evalai.main.entities.GrievanceEntity;
import com.evalai.main.entities.QuestionPaperEntity;
import com.evalai.main.entities.ResultEntity;
import com.evalai.main.entities.StudentAnswer;
import com.evalai.main.entities.SubQuestionEntity;
import com.evalai.main.enums.EvaluationStatus;
import com.evalai.main.enums.FailureReason;
import com.evalai.main.enums.FeedbackGeneratedBy;
import com.evalai.main.enums.GrievanceStatus;
import com.evalai.main.enums.GrievanceType;
import com.evalai.main.enums.OcrStatus;
import com.evalai.main.enums.ResultStatus;
import com.evalai.main.enums.TaskLogStatus;
import com.evalai.main.repositories.*;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;

import java.util.List;
import java.util.concurrent.ExecutorService;


import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates the full evaluation pipeline for one student's answer sheet.
 *
 * Flow:
 * 1. Admin triggers POST /pipeline/start for an exam
 * 2. PipelineService finds all PENDING answer sheets
 * 3. For each answer sheet:
 *    a. Gets raw image paths from disk
 *    b. Calls GrpcService → C++ preprocessing → cleaned images
 *    c. Updates TaskLog to PROCESSING
 *    d. Calls PythonService → POST /ocr/extract → queues Celery task
 *    e. Updates AnswerSheet.ocrStatus to PROCESSING
 * 4. Python processes async and calls back to POST /pipeline/callback
 * 5. PipelineService.handleCallback() saves all results to DB
 *
 * @author Vaibhav Sutar
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
public class PipelineService {
	private final AnswersheetRepository answerSheetRepository;
    private final TaskLogsRepository taskLogRepository;
    private final StudentAnswerRepository studentAnswerRepository;
    private final ResultRepository resultRepository;
    private final SubQuestionRepository subQuestionRepository;
    private final GrievanceRepository grievanceRepository;
    private final ExamRepository examRepository;
    private final QuestionPaperRepository questionPaperRepository;
    private final FeedbackRepository feedbackRepository;
    private final PipelineWorkerService pipelineWorkerService;
    private final ExecutorService executor;
    
    @Value("${app.upload.base-path}")
    private String uploadBasePath;
    
    private static final Logger logger =
            LoggerFactory.getLogger(PipelineService.class);
    
    /*-----------------------------------------------------------
    						TRIGGER PIPELINE
    ----------------------------------------------------------*/ 
    /**
     * Triggers the evaluation pipeline for all PENDING answer sheets in an exam.
     * Called by admin via POST /pipeline/start.
     *
     * Each answer sheet is processed independently.
     * Failures on one sheet do not block others.
     *
     * @param examId exam to evaluate
     * @return number of answer sheets queued for processing
     * @throws BadRequestException 
     */
    public int startPipeline(String examId) throws BadRequestException {

        // Step 1 — Verify exam exists
        ExamEntity exam = examRepository.findById(examId)
                .orElseThrow(() -> new BadRequestException("EXAM_NOT_FOUND"));

        // Step 2 — Get question paper for this exam
        List<QuestionPaperEntity> papers = questionPaperRepository.findByExam(exam);
        if (papers.isEmpty()) {
            throw new BadRequestException("NO_QUESTION_PAPER_FOUND");
        }
        // Use first available question paper
        QuestionPaperEntity questionPaper = papers.get(0);

        // Step 3 — Find all PENDING answer sheets
        List<AnswersheetEntity> pendingSheets = answerSheetRepository
        	    .findByExamAndEvaluationStatus(exam, EvaluationStatus.PENDING);
        
//        if (answerSheet.getEvaluationStatus() != EvaluationStatus.PENDING) {
//            logger.warn("Skipping already processing sheet {}", answerSheet.getId());
//            return;


        if (pendingSheets.isEmpty()) {
            throw new BadRequestException("NO_PENDING_SHEETS");
        }


        // Step 4 — Process each answer sheetF
        int queued = pendingSheets.size();
        
        String questionPaperId = questionPaper.getId();

        for (AnswersheetEntity sheet : pendingSheets) {
            String sheetId = sheet.getId();
            executor.submit(() -> {
                try {
                    pipelineWorkerService.processSingleAnswerSheet(sheetId, questionPaperId);
                } catch (Exception e) {
                    logger.error("Pipeline failed for {}", sheetId, e);
                }
            });
        }

        return queued;
        
    }
    
    
    /*----------------------------------------------------------
    						HANDLE CALLBACK
    -----------------------------------------------------------*/

    /**
     * Handles the callback from Python after OCR and scoring is complete.
     * Called by PipelineController when Python POSTs to /pipeline/callback.
     *
     * For each extracted answer:
     * - COMPLETED → save StudentAnswer + Result
     * - FAILED    → save StudentAnswer (raw text only) + Result (0 marks)
     *               + auto-create SYSTEM_GENERATED grievance for faculty review
     *
     * Updates AnswerSheet.evaluation_status based on overall results.
     * All DB writes for one student happen in a single transaction.
     *
     * @param payload callback payload from Python matching OcrResponse schema
     * @throws BadRequestException 
     */
    @Transactional
    public void handleCallback(CallbackPayload payload) throws BadRequestException {

        // Step 1 — Find answer sheet by ID from payload
        String answerSheetId = payload.getStudent().getAnswerSheetId();
        AnswersheetEntity answerSheet = answerSheetRepository
                .findById(answerSheetId)
                .orElseThrow(() -> new BadRequestException(
                        "ANSWER_SHEET_NOT_FOUND: " + answerSheetId
                ));

        // Step 2 — Find TaskLog and mark as completed
        taskLogRepository.findByAnswersheet(answerSheet)
                .stream().findFirst().ifPresent(taskLog -> {
                    taskLog.setStatus(TaskLogStatus.COMPLETED);
                    taskLog.setCompletedAt(LocalDateTime.now());
                    taskLogRepository.save(taskLog);
                });

        // Step 3 — Process each extracted answer
        int failedCount = 0;
        int completedCount = 0;
        float totalMarks = 0.0f;

        for (CallbackPayload.ExtractedAnswerPayload answer : payload.getExtractedAnswers()) {

            // Find the sub-question entity
            SubQuestionEntity subQuestion = subQuestionRepository
                    .findById(answer.getSubQuestionId())
                    .orElseThrow(() -> new RuntimeException(
                            "SUB_QUESTION_NOT_FOUND: " + answer.getSubQuestionId()
                    ));

            if ("COMPLETED".equals(answer.getStatus())) {
                // Save StudentAnswer with embedding
                StudentAnswer studentAnswer = buildStudentAnswer(
                        answerSheet, subQuestion, answer
                );
                studentAnswerRepository.save(studentAnswer);

                // Save Result with ai_marks = final_marks
                ResultEntity result = buildResult(
                        answerSheet, subQuestion, answer, studentAnswer
                );
                ResultEntity savedResult = resultRepository.save(result);

                // Save feedback if available  ← ADD THIS BLOCK
                if (answer.getFeedback() != null) {
                    saveFeedback(savedResult, answerSheet, answer.getFeedback());
                }

                totalMarks += savedResult.getFinalMarks();  // ← use savedResult not result
                completedCount++;

            } else {
                // FAILURE PATH 

                // Save StudentAnswer with failure reason
                StudentAnswer studentAnswer = buildFailedStudentAnswer(
                        answerSheet, subQuestion, answer
                );
                studentAnswerRepository.save(studentAnswer);

                // Save Result with 0 marks and FAILED status
                ResultEntity result = buildFailedResult(
                        answerSheet, subQuestion, answer, studentAnswer
                );
                ResultEntity savedResult = resultRepository.save(result);

                // Auto-create SYSTEM_GENERATED grievance for faculty review
                createSystemGrievance(savedResult, answerSheet, answer);

                failedCount++;
            }
        }

        // Step 4 — Update AnswerSheet total marks and evaluation status
        answerSheet.setMarks(totalMarks);
        answerSheet.setOcrStatus(OcrStatus.COMPLETED);
        answerSheet.setEvaluatedAt(LocalDateTime.now());

        // Determine overall evaluation status
        if (failedCount == 0) {
            answerSheet.setEvaluationStatus(EvaluationStatus.COMPLETED);
        } else if (completedCount > 0) {
            answerSheet.setEvaluationStatus(EvaluationStatus.COMPLETED_WITH_FAILURES);
        } else {
            answerSheet.setEvaluationStatus(EvaluationStatus.FAILED);
        }

        answerSheetRepository.save(answerSheet);

        logger.info(
                "Callback processed for sheet {} | completed: {} | failed: {} | "
                + "total_marks: {} | status: {}",
                answerSheetId, completedCount, failedCount,
                totalMarks, answerSheet.getEvaluationStatus()
        );
    }
    
    /*----------------------------------------------------------
    						PRIVATE BUILDERS
    ------------------------------------------------------------*/ 
    private StudentAnswer buildStudentAnswer(
            AnswersheetEntity answerSheet,
            SubQuestionEntity subQuestion,
            CallbackPayload.ExtractedAnswerPayload answer
    ) {
    		StudentAnswer sa = new StudentAnswer();
        sa.setAnswersheet(answerSheet);
        sa.setSubQuestion(subQuestion);
        sa.setExtractedText(answer.getExtractedText() != null ? answer.getExtractedText() : "");
        sa.setCleanedText(answer.getCleanedText());
        sa.setOcrConfidence(answer.getOcrConfidence() != null ? answer.getOcrConfidence() : 0.0f);

        if (answer.getEmbedding() != null) {
            float[] embedding = new float[answer.getEmbedding().size()];
            for (int i = 0; i < answer.getEmbedding().size(); i++) {
                embedding[i] = answer.getEmbedding().get(i);
            }
            sa.setEmbedding(embedding);
        }
        return sa;
    }

    private StudentAnswer buildFailedStudentAnswer(
            AnswersheetEntity answerSheet,
            SubQuestionEntity subQuestion,
            CallbackPayload.ExtractedAnswerPayload answer
    ) {
    		StudentAnswer sa = new StudentAnswer();
        sa.setAnswersheet(answerSheet);
        sa.setSubQuestion(subQuestion);
        sa.setExtractedText(answer.getExtractedText() != null ? answer.getExtractedText() : "");
        sa.setCleanedText(null);
        sa.setOcrConfidence(answer.getOcrConfidence() != null ? answer.getOcrConfidence() : 0.0f);
        sa.setFailureReason(
                answer.getFailureReason() != null
                        ? FailureReason.valueOf(answer.getFailureReason())
                        : null
        );
        return sa;
    }

    private ResultEntity buildResult(
            AnswersheetEntity answerSheet,
            SubQuestionEntity subQuestion,
            CallbackPayload.ExtractedAnswerPayload answer,
            StudentAnswer studentAnswer
    ) {
    	ResultEntity result = new ResultEntity();
        result.setAnswersheet(answerSheet);
        result.setStudent(answerSheet.getStudent());
        result.setSubQuestion(subQuestion);
        result.setStudentAnswer(studentAnswer);
        
        // 1. Get the marks from the Python payload (ai_marks is the actual score, e.g., 5.2)
        float aiMarks = answer.getAiMarks() != null ? answer.getAiMarks() : 0.0f;
        
        // 2. Set both AI marks and Final marks (assuming they start as the same)
        result.setAiMarks(aiMarks);
        result.setFinalMarks(aiMarks); 
        
        // 3. Set metadata
        result.setSimilarityScore(answer.getSimilarityScore());
        result.setTotalMarks(subQuestion.getMarks()); // The maximum possible marks
        result.setStatus(ResultStatus.COMPLETED);
        result.setIsOverriden(false);
        
        return result;
    }

    private ResultEntity buildFailedResult(
            AnswersheetEntity answerSheet,
            SubQuestionEntity subQuestion,
            CallbackPayload.ExtractedAnswerPayload answer,
            StudentAnswer studentAnswer
    ) {
        ResultEntity result = new ResultEntity();
        result.setAnswersheet(answerSheet);
        result.setStudent(answerSheet.getStudent());
        result.setSubQuestion(subQuestion);
        result.setStudentAnswer(studentAnswer);
        result.setAiMarks(0.0f);
        result.setFinalMarks(0.0f);
        result.setTotalMarks(subQuestion.getMarks());
        result.setSimilarityScore(null);
        result.setStatus(ResultStatus.PENDING_REVIEW);
        result.setIsOverriden(false);
        return result;
    }

    private void createSystemGrievance(
            ResultEntity result,
            AnswersheetEntity answerSheet,
            CallbackPayload.ExtractedAnswerPayload answer
    ) {
        GrievanceEntity grievance = new GrievanceEntity();
        grievance.setResult(result);
        grievance.setStudent(answerSheet.getStudent());
        grievance.setGrievanceType(GrievanceType.SYSTEM_GENERATED);
        grievance.setStatus(GrievanceStatus.UNDER_REVIEW);
        grievance.setFailureReason(answer.getFailureReason());
        grievance.setStudentReason(null); // system generated — no student reason
        grievanceRepository.save(grievance);

        logger.info(
                "System grievance created for sub-question {} | reason: {}",
                answer.getSubQuestionId(), answer.getFailureReason()
        );
    }
    
    
    
    
    private void saveFeedback(
            ResultEntity result,
            AnswersheetEntity answerSheet,
            CallbackPayload.ExtractedAnswerPayload.FeedbackPayload feedback
    ) {
        FeedbackEntity feedbackEntity = new FeedbackEntity();
        feedbackEntity.setResult(result);
        feedbackEntity.setAnswersheet(answerSheet);
        feedbackEntity.setStrengths(
            feedback.getStrengths() != null ? feedback.getStrengths() : ""
        );
        feedbackEntity.setWeakness(
            feedback.getWeakness() != null ? feedback.getWeakness() : ""
        );
        feedbackEntity.setSuggestions(
            feedback.getSuggestions() != null ? feedback.getSuggestions() : ""
        );
        feedbackEntity.setOverallFeedback(
            feedback.getOverallFeedback() != null ? feedback.getOverallFeedback() : ""
        );
        feedbackEntity.setKeyConceptsMissed(
            feedback.getKeyConceptsMissed() != null
                ? feedback.getKeyConceptsMissed()
                : new ArrayList<>()
        );
        feedbackEntity.setGeneratedBy(FeedbackGeneratedBy.GEMINI);
        feedbackRepository.save(feedbackEntity);

        logger.info("Feedback saved for result {}", result.getId());
    }
}
