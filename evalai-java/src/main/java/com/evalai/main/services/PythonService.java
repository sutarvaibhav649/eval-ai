package com.evalai.main.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.evalai.main.entities.AnswersheetEntity;
import com.evalai.main.entities.ExamEntity;
import com.evalai.main.entities.ModelAnswerEntity;
import com.evalai.main.entities.QuestionPaperEntity;
import com.evalai.main.entities.SubjectEntity;
import com.evalai.main.repositories.ModelAnswerRepository;
import com.evalai.main.repositories.SubQuestionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Handles all HTTP communication between Java and the Python FastAPI service.
 * Responsible for sending OCR extraction requests to Python.
 * Python processes asynchronously via Celery and calls back to /pipeline/callback.
 *
 * @author Vaibhav Sutar
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
public class PythonService {
	private final RestTemplate restTemplate;
	private final ModelAnswerRepository modelAnswerRepository;
	
	@Value("${app.python.base-url}")
    private String pythonBaseUrl;
	
	@Value("${app.java.base-url:http://evalai-java:8081}")
	private String javaBaseUrl;
	
	
	/**
     * Sends an OCR extraction request to Python for one student's answer sheet.
     *
     * Builds the full request payload including:
     * - exam and student context
     * - paths to cleaned images
     * - all sub-questions with their model answer embeddings
     *
     * Python receives this, queues a Celery task, and returns immediately.
     * Results come back via POST /pipeline/callback.
     *
     * @param answerSheet   the student's answer sheet entity
     * @param taskId        unique task ID Java generated for this job
     * @param imagePaths    list of cleaned image paths from C++ output
     * @param questionPaper the question paper for this exam
     * @return celery task ID returned by Python
     */
	public String sendOcrRequest(
			AnswersheetEntity answersheet,
			String taskId,
			List<String> imagePaths,
			QuestionPaperEntity questionPaper
			
	) {
		ExamEntity exam = answersheet.getExam();
		SubjectEntity subject = exam.getSubject();
		
		// context block
		Map<String, Object> context = new HashMap<>();
		context.put("exam_id", exam.getId());
		context.put("exam_name", exam.getTitle());
		context.put("course_id", subject.getId());
		context.put("course_name", subject.getName());
		context.put("subject_code", subject.getCode());
		context.put("subject_name", subject.getName());  
		context.put("academic_year", exam.getAcademicYear());
		context.put("question_paper_id", questionPaper.getId());
		context.put("question_paper_set", questionPaper.getSetLable());
        
        // student block
        Map<String, Object> student = new HashMap<>();
        student.put("student_id", answersheet.getStudent().getId());
        student.put("answer_sheet_id", answersheet.getId());
        
        // questions array — each sub-question with its model answer embedding
        List<Map<String, Object>> questions = buildQuestionsPayload(questionPaper);
     // Assemble full request
        Map<String, Object> payload = new HashMap<>();
        payload.put("task_id", taskId);
        payload.put("context", context);
        payload.put("student", student);
        payload.put("raw_image_paths", imagePaths);
        payload.put("questions", questions);
        payload.put("callback_url", javaBaseUrl + "/pipeline/callback");

        // Send to Python — Python returns immediately, processing is async
        String url = pythonBaseUrl + "/ocr/extract";
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url, payload, Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String celeryTaskId = (String) response.getBody().get("celery_task_id");
                return celeryTaskId;
            }

            throw new RuntimeException("Python returned non-200 response");

        } catch (Exception e) {
            throw new RuntimeException("PYTHON_UNAVAILABLE: " + e.getMessage());
        }
	}
	
	/**
     * Builds the questions payload for the OCR request.
     * Fetches all sub-questions and their model answer embeddings.
     *
     * Only sub-questions with model answers AND embeddings are included.
     * Sub-questions missing embeddings are logged as warnings.
     */
	private List<Map<String, Object>> buildQuestionsPayload(QuestionPaperEntity questionPaper) {
	    List<Map<String, Object>> questions = new ArrayList<>();

	    questionPaper.getQuestions().forEach(question -> {
	        logger.info(">>> Processing question: {}", question.getId());
	        question.getSubQuestions().forEach(subQuestion -> {
	            try {
	                Optional<ModelAnswerEntity> maOpt = modelAnswerRepository
	                        .findBySubQuestion(subQuestion);
	              
	                if (maOpt.isEmpty()) {
	                  
	                    return;
	                }

	                ModelAnswerEntity modelAnswer = maOpt.get();
	               

	                if (modelAnswer.getEmbedding() == null) {
	                    logger.warn("Sub-question {} has no embedding — skipping",
	                            subQuestion.getId());
	                    return;
	                }

	              
	                float[] embeddingArray = modelAnswer.getEmbedding();
	                if (embeddingArray == null) {
	                 
	                    return;
	                }
	                List<Float> embeddingList = new ArrayList<>();
	                for (float f : embeddingArray) {
	                    embeddingList.add(f);
	                }

	                Map<String, Object> q = new HashMap<>();
	                q.put("sub_question_id", subQuestion.getId());
	                q.put("sub_question_label", subQuestion.getSubQuestionLabel());
	                q.put("question_number", question.getQuestionNumber());
	                q.put("marks", subQuestion.getMarks());
	                q.put("model_answer_embedding", embeddingList);
	                q.put("model_answer_text", modelAnswer.getAnswerText());      
	                q.put("question_text", subQuestion.getQuestionText());        
	                q.put("key_concepts", modelAnswer.getKeyConcepts() != null    
	                        ? modelAnswer.getKeyConcepts()
	                        : new ArrayList<>());
	                questions.add(q);
	             

	            } catch (Exception e) {
	                logger.error(">>> CRASH at sub-question {}: {} | cause: {}",
	                        subQuestion.getId(),
	                        e.getMessage(),
	                        e.getCause() != null ? e.getCause().getMessage() : "none",
	                        e  // full stack trace
	                );
	            }
	        });
	    });

	    return questions;
	}
    
    private static final org.slf4j.Logger logger =
            org.slf4j.LoggerFactory.getLogger(PythonService.class);
}
