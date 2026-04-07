package com.evalai.main.services;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;

import com.evalai.main.dtos.ProcessingContext;
import com.evalai.main.entities.AnswersheetEntity;
import com.evalai.main.entities.QuestionPaperEntity;
import com.evalai.main.entities.TaskLogsEntity;
import com.evalai.main.enums.EvaluationStatus;
import com.evalai.main.enums.OcrStatus;
import com.evalai.main.enums.TaskLogStatus;
import com.evalai.main.repositories.AnswersheetRepository;
import com.evalai.main.repositories.QuestionPaperRepository;
import com.evalai.main.repositories.TaskLogsRepository;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class PipelineWorkerService implements ApplicationContextAware {

	private final TaskLogsRepository taskLogRepository;
    private final AnswersheetRepository answerSheetRepository;
    private final PythonService pythonService;
    private final QuestionPaperRepository questionPaperRepository;
    
    
    private ApplicationContext applicationContext;
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    private PipelineWorkerService self() {
        return applicationContext.getBean(PipelineWorkerService.class);
    }


    @Value("${app.upload.base-path}")
    private String uploadBasePath;

    private static final Logger logger =
            LoggerFactory.getLogger(PipelineWorkerService.class);

    // 🚀 ENTRY POINT (NO TRANSACTION HERE)
    public void processSingleAnswerSheet(String answerSheetId, String questionPaperId) {

        // Reload entities fresh in this thread — no stale session
        AnswersheetEntity answerSheet = answerSheetRepository.findById(answerSheetId)
                .orElseThrow(() -> new RuntimeException("ANSWER_SHEET_NOT_FOUND: " + answerSheetId));

        // Load via self() so @Transactional fires and session stays open during initialize
        QuestionPaperEntity questionPaper = self().loadQuestionPaperWithAll(questionPaperId);

        ProcessingContext context = self().markProcessing(answerSheet);

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

            self().updateCeleryTaskId(context.taskId(), celeryTaskId);

        } catch (Exception e) {
            logger.error("Python call failed for {}", answerSheetId, e);
            self().markFailed(context.taskId());
        }
    }

    // 🥇 PHASE 1 (LOCK + UPDATE)
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

        return new ProcessingContext(
                taskLog.getTaskId(),
                answerSheet,
                rawImagePaths
        );
    }

    // 🥉 PHASE 2 UPDATE (small transaction)
    @Transactional
    public void updateCeleryTaskId(String taskId, String celeryTaskId) {
        TaskLogsEntity taskLog = taskLogRepository
                .findByTaskId(taskId)
                .orElseThrow(() -> new RuntimeException("TASK_LOG_NOT_FOUND"));

        taskLog.setCeleryTaskId(celeryTaskId);
        taskLogRepository.save(taskLog);
    }

    // ❌ FAILURE HANDLING
    @Transactional
    public void markFailed(String taskId) {
        TaskLogsEntity taskLog = taskLogRepository
                .findByTaskId(taskId)
                .orElseThrow(() -> new RuntimeException("TASK_LOG_NOT_FOUND"));

        taskLog.setStatus(TaskLogStatus.FAILED);
        taskLogRepository.save(taskLog);
    }

    /*----------------------------------------------------------*/
    /* PRIVATE HELPERS */
    /*----------------------------------------------------------*/

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
    
    @Transactional(readOnly = true)
    public QuestionPaperEntity loadQuestionPaperWithAll(String questionPaperId) {
        QuestionPaperEntity questionPaper = questionPaperRepository.findByIdWithQuestions(questionPaperId)
                .orElseThrow(() -> new RuntimeException("QUESTION_PAPER_NOT_FOUND: " + questionPaperId));

        // Session is active here — safe to initialize
        questionPaper.getQuestions().forEach(q -> Hibernate.initialize(q.getSubQuestions()));

        return questionPaper;
    }
}