package com.evalai.main.services;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.evalai.main.dtos.response.CallbackPayload;
import com.evalai.main.entities.*;
import com.evalai.main.enums.*;
import com.evalai.main.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.evalai.main.dtos.ProcessingContext;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class PipelineWorkerService {

    private final TaskLogsRepository taskLogRepository;
    private final AnswersheetRepository answerSheetRepository;
    private final PythonService pythonService;
    private final QuestionPaperRepository questionPaperRepository;
    private final StudentAnswerRepository studentAnswerRepository;
    private final ResultRepository resultRepository;
    private final SubQuestionRepository subQuestionRepository;
    private final GrievanceRepository grievanceRepository;
    private final FeedbackRepository feedbackRepository;

    /**
     * FIX: Replaced ApplicationContextAware + getBean(self) anti-pattern
     * with @Lazy self-injection — cleaner and Spring-idiomatic.
     *
     * @Lazy breaks the circular dependency Spring detects at startup.
     * The proxy is still created, so @Transactional on methods called
     * via `self` will work correctly.
     */
    @Lazy
    @Autowired
    private PipelineWorkerService self;

    @Value("${app.upload.base-path}")
    private String uploadBasePath;

    private static final Logger logger = LoggerFactory.getLogger(PipelineWorkerService.class);

    // ENTRY POINT

    public void processSingleAnswerSheet(String answerSheetId, String questionPaperId) {

        AnswersheetEntity answerSheet = answerSheetRepository.findByIdWithExamAndSubjects(answerSheetId)
                .orElseThrow(() -> new RuntimeException("ANSWER_SHEET_NOT_FOUND: " + answerSheetId));

        // FIX: use self (proxy) so @Transactional fires on this method
        QuestionPaperEntity questionPaper = self.loadQuestionPaperWithAll(questionPaperId);
        ProcessingContext context = self.markProcessing(answerSheet);

        if (context == null) {
            return;
        }

        try {
            logger.info("[PIPELINE] Sending to Python | sheet: {}", answerSheetId);

            String celeryTaskId = pythonService.sendOcrRequest(
                    context.answerSheet(),
                    context.taskId(),
                    context.rawImagePaths(),
                    questionPaper
            );

            self.updateCeleryTaskId(context.taskId(), celeryTaskId);

        } catch (Exception e) {
            logger.error("Python call failed for {}", answerSheetId, e);
            self.markFailed(context.taskId());
        }
    }

    //PHASE 1: Mark as processing

    @Transactional
    public ProcessingContext markProcessing(AnswersheetEntity answerSheet) {

        String examId = answerSheet.getExam().getId();
        String studentId = answerSheet.getStudent().getId();

        List<String> rawImagePaths = getRawImagePaths(examId, studentId);

        if (rawImagePaths.isEmpty()) {
            throw new RuntimeException("NO_IMAGES_FOUND for " + answerSheet.getId());
        }

        TaskLogsEntity taskLog = taskLogRepository
                .findByAnswersheetForUpdate(answerSheet)
                .orElseThrow(() -> new RuntimeException(
                        "TASK_LOG_NOT_FOUND " + answerSheet.getId()
                ));

        if (taskLog.getStatus() != TaskLogStatus.QUEUED) {
            logger.warn("Skipping duplicate pipeline for {}", answerSheet.getId());
            return null;
        }

        taskLog.setStatus(TaskLogStatus.PROCESSING);
        taskLog.setStartedAt(LocalDateTime.now());
        taskLogRepository.save(taskLog);

        answerSheet.setOcrStatus(OcrStatus.PROCESSING);
        answerSheet.setEvaluationStatus(EvaluationStatus.PROCESSING);
        answerSheetRepository.save(answerSheet);

        return new ProcessingContext(taskLog.getTaskId(), answerSheet, rawImagePaths);
    }

    //  PHASE 2: Update celery ID

    @Transactional
    public void updateCeleryTaskId(String taskId, String celeryTaskId) {
        TaskLogsEntity taskLog = taskLogRepository
                .findByTaskId(taskId)
                .orElseThrow(() -> new RuntimeException("TASK_LOG_NOT_FOUND"));
        taskLog.setCeleryTaskId(celeryTaskId);
        taskLogRepository.save(taskLog);
    }

    // FAILURE HANDLING

    @Transactional
    public void markFailed(String taskId) {
        TaskLogsEntity taskLog = taskLogRepository
                .findByTaskId(taskId)
                .orElseThrow(() -> new RuntimeException("TASK_LOG_NOT_FOUND"));
        taskLog.setStatus(TaskLogStatus.FAILED);
        taskLogRepository.save(taskLog);
    }

    // ANSWER PROCESSING (called per-answer from PipelineService)

    /**
     * FIX: Each answer is now processed in its own transaction.
     * If saving answer #5 fails, answers #1–4 are already committed.
     * Returns the awarded marks (0.0 for FAILED answers).
     */
    @Transactional
    public float processOneAnswer(
            AnswersheetEntity answerSheet,
            CallbackPayload.ExtractedAnswerPayload answer
    ) {
        SubQuestionEntity subQuestion = subQuestionRepository
                .findById(answer.getSubQuestionId())
                .orElseThrow(() -> new RuntimeException(
                        "SUB_QUESTION_NOT_FOUND: " + answer.getSubQuestionId()
                ));

        if ("COMPLETED".equals(answer.getStatus())) {
            StudentAnswer studentAnswer = buildStudentAnswer(answerSheet, subQuestion, answer);
            studentAnswerRepository.save(studentAnswer);

            ResultEntity result = buildResult(answerSheet, subQuestion, answer, studentAnswer);
            ResultEntity savedResult = resultRepository.save(result);

            if (answer.getFeedback() != null) {
                saveFeedback(savedResult, answerSheet, answer.getFeedback());
            }

            return savedResult.getFinalMarks() != null ? savedResult.getFinalMarks() : 0.0f;

        } else {
            StudentAnswer studentAnswer = buildFailedStudentAnswer(answerSheet, subQuestion, answer);
            studentAnswerRepository.save(studentAnswer);

            ResultEntity result = buildFailedResult(answerSheet, subQuestion, answer, studentAnswer);
            ResultEntity savedResult = resultRepository.save(result);

            createSystemGrievance(savedResult, answerSheet, answer);
            return 0.0f;
        }
    }

    // Entity builders

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

        float aiMarks = answer.getAiMarks() != null ? answer.getAiMarks() : 0.0f;
        result.setAiMarks(aiMarks);
        result.setFinalMarks(aiMarks);
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
        grievance.setStudentReason(null);
        grievanceRepository.save(grievance);

        logger.info("System grievance created for sub-question {} | reason: {}",
                answer.getSubQuestionId(), answer.getFailureReason());
    }

    private void saveFeedback(
            ResultEntity result,
            AnswersheetEntity answerSheet,
            CallbackPayload.ExtractedAnswerPayload.FeedbackPayload feedback
    ) {
        FeedbackEntity feedbackEntity = new FeedbackEntity();
        feedbackEntity.setResult(result);
        feedbackEntity.setAnswersheet(answerSheet);
        feedbackEntity.setStrengths(feedback.getStrengths() != null ? feedback.getStrengths() : "");
        feedbackEntity.setWeakness(feedback.getWeakness() != null ? feedback.getWeakness() : "");
        feedbackEntity.setSuggestions(feedback.getSuggestions() != null ? feedback.getSuggestions() : "");
        feedbackEntity.setOverallFeedback(feedback.getOverallFeedback() != null ? feedback.getOverallFeedback() : "");
        feedbackEntity.setKeyConceptsMissed(
                feedback.getKeyConceptsMissed() != null ? feedback.getKeyConceptsMissed() : new ArrayList<>()
        );
        feedbackEntity.setGeneratedBy(FeedbackGeneratedBy.GEMINI);
        feedbackRepository.save(feedbackEntity);

        logger.info("Feedback saved for result {}", result.getId());
    }

    // Load with lazy collections

    @Transactional(readOnly = true)
    public QuestionPaperEntity loadQuestionPaperWithAll(String questionPaperId) {
        QuestionPaperEntity questionPaper = questionPaperRepository
                .findByIdWithQuestions(questionPaperId)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND"));

        // Force load the subjects collection while the session is active
        if (questionPaper.getExam() != null) {
            Hibernate.initialize(questionPaper.getExam().getSubjects());
        }

        // Also initialize questions/subquestions as you were doing
        questionPaper.getQuestions().forEach(q -> Hibernate.initialize(q.getSubQuestions()));

        return questionPaper;
    }

    // ─── File helpers ─────────────────────────────────────────────────────────

    private List<String> getRawImagePaths(String examId, String studentId) {
        String rawDir = uploadBasePath + File.separator + "raw_images"
                + File.separator + examId + File.separator + studentId;
        File dir = new File(rawDir);

        if (!dir.exists() || !dir.isDirectory()) {
            logger.error("Raw images directory not found: {}", rawDir);
            return new ArrayList<>();
        }

        File[] files = dir.listFiles();
        logger.info("Files found: {}", files != null ? files.length : 0);

        return Arrays.stream(files)
                .filter(f -> f.getName().endsWith(".png"))
                .sorted(Comparator.comparing(File::getName))
                .map(File::getAbsolutePath)
                .toList();
    }
}