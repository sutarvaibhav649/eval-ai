package com.evalai.main.services;

import com.evalai.main.dtos.response.CallbackPayload;
import com.evalai.main.entities.*;
import com.evalai.main.enums.*;
import com.evalai.main.utils.BadRequestException;
import com.evalai.main.repositories.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

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

    private static final Logger logger = LoggerFactory.getLogger(PipelineService.class);

    /*-----------------------------------------------------------
                       TRIGGER PIPELINE
    ----------------------------------------------------------*/
    public int startPipeline(String examId) throws BadRequestException {

        ExamEntity exam = examRepository.findById(examId)
                .orElseThrow(() -> new BadRequestException("EXAM_NOT_FOUND"));

        List<QuestionPaperEntity> papers = questionPaperRepository.findByExam(exam);
        if (papers.isEmpty()) {
            throw new BadRequestException("NO_QUESTION_PAPER_FOUND");
        }
        QuestionPaperEntity questionPaper = papers.get(0);

        List<AnswersheetEntity> pendingSheets = answerSheetRepository
                .findByExamAndEvaluationStatus(exam, EvaluationStatus.PENDING);

        if (pendingSheets.isEmpty()) {
            throw new BadRequestException("NO_PENDING_SHEETS");
        }

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
     * FIX 1: Was one giant @Transactional — if answer #5 of 10 failed mid-way,
     * ALL 10 results would be rolled back. Students would lose all results.
     *
     * Fix: Sheet-level metadata is in its own transaction (updateAnswerSheetStatus).
     * Each answer is processed in its own transaction via processOneAnswer().
     * One answer failing does NOT roll back others.
     *
     * FIX 2: Callback security — callers should validate a shared secret header.
     * The secret is checked in PipelineController before calling this method.
     */
    public void handleCallback(CallbackPayload payload) throws BadRequestException {

        String answerSheetId = payload.getStudent().getAnswerSheetId();
        AnswersheetEntity answerSheet = answerSheetRepository
                .findById(answerSheetId)
                .orElseThrow(() -> new BadRequestException(
                        "ANSWER_SHEET_NOT_FOUND: " + answerSheetId
                ));

        // Mark task log as completed (its own small transaction)
        markTaskLogCompleted(answerSheet);

        int failedCount = 0;
        int completedCount = 0;
        float totalMarks = 0.0f;

        // FIX: Process each answer in its own transaction
        // One failure does NOT roll back the others
        for (CallbackPayload.ExtractedAnswerPayload answer : payload.getExtractedAnswers()) {
            try {
                float marks = pipelineWorkerService.processOneAnswer(answerSheet, answer);
                if ("COMPLETED".equals(answer.getStatus())) {
                    totalMarks += marks;
                    completedCount++;
                } else {
                    failedCount++;
                }
            } catch (Exception e) {
                logger.error("Failed to save answer for sub-question {}: {}",
                        answer.getSubQuestionId(), e.getMessage());
                failedCount++;
            }
        }

        // Update overall sheet status in its own transaction
        updateAnswerSheetStatus(answerSheet, completedCount, failedCount, totalMarks);

        logger.info(
                "Callback processed for sheet {} | completed: {} | failed: {} | "
                        + "total_marks: {}",
                answerSheetId, completedCount, failedCount, totalMarks
        );
    }

    // ─── Private transaction helpers ──────────────────────────────────────────

    @Transactional
    public void markTaskLogCompleted(AnswersheetEntity answerSheet) {
        taskLogRepository.findByAnswersheet(answerSheet)
                .stream().findFirst().ifPresent(taskLog -> {
                    taskLog.setStatus(TaskLogStatus.COMPLETED);
                    taskLog.setCompletedAt(LocalDateTime.now());
                    taskLogRepository.save(taskLog);
                });
    }

    @Transactional
    public void updateAnswerSheetStatus(
            AnswersheetEntity answerSheet,
            int completedCount,
            int failedCount,
            float totalMarks
    ) {
        // Reload fresh to avoid stale state
        AnswersheetEntity fresh = answerSheetRepository
                .findById(answerSheet.getId())
                .orElse(answerSheet);

        fresh.setMarks(totalMarks);
        fresh.setOcrStatus(OcrStatus.COMPLETED);
        fresh.setEvaluatedAt(LocalDateTime.now());

        if (failedCount == 0) {
            fresh.setEvaluationStatus(EvaluationStatus.COMPLETED);
        } else if (completedCount > 0) {
            fresh.setEvaluationStatus(EvaluationStatus.COMPLETED_WITH_FAILURES);
        } else {
            fresh.setEvaluationStatus(EvaluationStatus.FAILED);
        }

        answerSheetRepository.save(fresh);
    }
}