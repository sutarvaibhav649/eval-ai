package com.evalai.main.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.evalai.main.dtos.response.CallbackPayload;
import com.evalai.main.entities.AnswersheetEntity;
import com.evalai.main.entities.ExamEntity;
import com.evalai.main.entities.GrievanceEntity;
import com.evalai.main.entities.QuestionPaperEntity;
import com.evalai.main.entities.ResultEntity;
import com.evalai.main.entities.StudentAnswer;
import com.evalai.main.entities.SubQuestionEntity;
import com.evalai.main.entities.TaskLogsEntity;
import com.evalai.main.enums.EvaluationStatus;
import com.evalai.main.enums.FailureReason;
import com.evalai.main.enums.GrievanceStatus;
import com.evalai.main.enums.GrievanceType;
import com.evalai.main.enums.OcrStatus;
import com.evalai.main.enums.ResultStatus;
import com.evalai.main.enums.TaskLogStatus;
import com.evalai.main.repositories.*;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

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
    private final GrpcService grpcService;
    private final PythonService pythonService;
    
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
     */
    public int startPipeline(String examId) {

        // Step 1 — Verify exam exists
        ExamEntity exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("EXAM_NOT_FOUND"));

        // Step 2 — Get question paper for this exam
        List<QuestionPaperEntity> papers = questionPaperRepository.findByExam(exam);
        if (papers.isEmpty()) {
            throw new RuntimeException("NO_QUESTION_PAPER_FOUND");
        }
        // Use first available question paper
        QuestionPaperEntity questionPaper = papers.get(0);

        // Step 3 — Find all PENDING answer sheets
        List<AnswersheetEntity> pendingSheets = answerSheetRepository
                .findByExamAndEvaluationStatus(exam, EvaluationStatus.PENDING);

        if (pendingSheets.isEmpty()) {
            throw new RuntimeException("NO_PENDING_SHEETS");
        }


        // Step 4 — Process each answer sheet
        int queued = 0;
        for (AnswersheetEntity answerSheet : pendingSheets) {
            try {
                processSingleAnswerSheet(answerSheet, questionPaper);
                queued++;
            } catch (Exception e) {
                logger.error(
                        "Failed to queue answer sheet {} for student {}: {}",
                        answerSheet.getId(),
                        answerSheet.getStudent().getId(),
                        e.getMessage()
                );
                // Mark this sheet as FAILED but continue with others
                answerSheet.setEvaluationStatus(EvaluationStatus.FAILED);
                answerSheetRepository.save(answerSheet);
            }
        }

        return queued;
    }
    /**
     * Processes a single answer sheet through the pipeline.
     *
     * Steps:
     * 1. Collect raw image paths from disk
     * 2. Send to C++ for preprocessing → get cleaned image paths
     * 3. Update TaskLog to PROCESSING
     * 4. Send to Python for OCR + scoring
     * 5. Update AnswerSheet.ocrStatus to PROCESSING
     */
    private void processSingleAnswerSheet(
            AnswersheetEntity answerSheet,
            QuestionPaperEntity questionPaper
    ) {
        String examId = answerSheet.getExam().getId();
        String studentId = answerSheet.getStudent().getId();

        List<String> rawImagePaths = getRawImagePaths(examId, studentId);

        logger.info("Step 2 — Getting task log");
        List<TaskLogsEntity> taskLogs = taskLogRepository.findByAnswersheet(answerSheet);
        logger.info("Step 2 done — found {} task logs", taskLogs.size());
      
        if (taskLogs.isEmpty()) {
            throw new RuntimeException("TASK_LOG_NOT_FOUND for sheet " + answerSheet.getId());
        }
        TaskLogsEntity taskLog = taskLogs.get(0);
        String taskId = taskLog.getTaskId();

        // Step 3 — Call C++ for preprocessing (or skip in dev mode)
        List<String> cleanedImagePaths = grpcService.preprocessImages(
                answerSheet, taskId, rawImagePaths
        );

        // Step 4 — Update TaskLog to PROCESSING
        taskLog.setStatus(TaskLogStatus.PROCESSING);
        taskLog.setStartedAt(LocalDateTime.now());
        taskLogRepository.save(taskLog);

        // Step 5 — Update AnswerSheet status
        answerSheet.setOcrStatus(OcrStatus.PROCESSING);
        answerSheet.setEvaluationStatus(EvaluationStatus.PROCESSING);
        answerSheetRepository.save(answerSheet);

        // Step 6 — Send to Python for OCR + scoring
        String celeryTaskId = pythonService.sendOcrRequest(
                answerSheet, taskId, cleanedImagePaths, questionPaper
        );

        // Step 7 — Store Celery task ID in TaskLog for tracking
        taskLog.setCeleryTaskId(celeryTaskId);
        taskLogRepository.save(taskLog);

        logger.info(
                "Answer sheet {} queued | student: {} | celery_task_id: {}",
                answerSheet.getId(), studentId, celeryTaskId
        );
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
     */
    @Transactional
    public void handleCallback(CallbackPayload payload) {

        // Step 1 — Find answer sheet by ID from payload
        String answerSheetId = payload.getStudent().getAnswerSheetId();
        AnswersheetEntity answerSheet = answerSheetRepository
                .findById(answerSheetId)
                .orElseThrow(() -> new RuntimeException(
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
                // SUCCESS PATH
                // Save StudentAnswer with embedding
                StudentAnswer studentAnswer = buildStudentAnswer(
                        answerSheet, subQuestion, answer
                );
                studentAnswerRepository.save(studentAnswer);

                // Save Result with ai_marks = final_marks
                ResultEntity result = buildResult(
                        answerSheet, subQuestion, answer, studentAnswer
                );
                resultRepository.save(result);

                totalMarks += result.getFinalMarks(); 
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
    		System.out.println(answerSheet.getMarks());
        ResultEntity result = new ResultEntity();
        result.setAnswersheet(answerSheet);
        result.setStudent(answerSheet.getStudent());
        result.setSubQuestion(subQuestion);
        result.setStudentAnswer(studentAnswer);
        
        
        // Total Marks Logic
        float aiNormalizedScore = answer.getAiMarks() != null ? answer.getAiMarks() : 0.0f;
        float maxPossibleMarks = subQuestion.getMarks(); // Ensure this field in SubQuestion is correct
        float calculatedMarks = aiNormalizedScore * maxPossibleMarks;
        
        float finalCalculatedMarks = Math.round(calculatedMarks * 100.0f) / 100.0f;
        
        result.setAiMarks(aiNormalizedScore);        // Store the raw 0.4
        result.setFinalMarks(finalCalculatedMarks);  // Store the 4.0
        
        System.out.println("AI marks: "+answer.getAiMarks());
        System.out.println("Total marks: "+finalCalculatedMarks);
        
        result.setSimilarityScore(answer.getSimilarityScore());
        result.setTotalMarks(subQuestion.getMarks());
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
    
    
    /*---------------------------------------------------------------------
    							PRIVATE HELPERS
    --------------------------------------------------------------------*/ 

    /**
     * Collects all raw image paths for a student's answer sheet from disk.
     * Reads from upload/raw_images/examId/studentId/ directory.
     */
    private List<String> getRawImagePaths(String examId, String studentId) {
        String rawDir = uploadBasePath + File.separator + "raw_images"
                + File.separator + examId + File.separator + studentId;

        File dir = new File(rawDir);
        

        if (!dir.exists() || !dir.isDirectory()) {
            logger.error("Raw images directory not found: {}", rawDir);
            return new ArrayList<>();
        }

        File[] files = dir.listFiles();
        logger.info("Files found: {}", files != null ? files.length : 0);  // ← add this

        return Arrays.stream(files)
                .filter(f -> f.getName().endsWith(".png"))
                .sorted(Comparator.comparing(File::getName))
                .map(File::getAbsolutePath)
                .collect(java.util.stream.Collectors.toList());
    }
}
