package com.evalai.main.services;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.evalai.main.dtos.request.GrievanceReviewRequest;
import com.evalai.main.dtos.request.ModelAnswerRequestDTO;
import com.evalai.main.dtos.request.QuestionPaperRequestDTO;
import com.evalai.main.dtos.request.QuestionRequestDTO;
import com.evalai.main.dtos.request.SubQuestionRequestDTO;
import com.evalai.main.entities.ExamEntity;
import com.evalai.main.entities.GrievanceEntity;
import com.evalai.main.entities.ModelAnswerEntity;
import com.evalai.main.entities.QuestionEntity;
import com.evalai.main.entities.QuestionPaperEntity;
import com.evalai.main.entities.ResultEntity;
import com.evalai.main.entities.SubQuestionEntity;
import com.evalai.main.entities.UserEntity;
import com.evalai.main.enums.GrievanceStatus;
import com.evalai.main.enums.QuestionPaperStatus;
import com.evalai.main.repositories.*;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

/**
 * Handles all Faculty business logic:
 * 1. Question Paper creation and upload
 * 2. Question and SubQuestion creation
 * 3. Model Answer submission + embedding generation via Python
 * 4. Grievance review and resolution
 *
 * @author Vaibhav Sutar
 * @version 1.0
 */

@Service
@RequiredArgsConstructor
public class FacultyService {

	private final QuestionPaperRepository questionPaperRepository;
	private final QuestionRepository questionRepository;
	private final SubQuestionRepository subQuestionRepository;
	private final ModelAnswerRepository modelAnswerRepository;
	private final GrievanceRepository grievanceRepository;
	private final ResultRepository resultRepository;
	private final ExamRepository examRepository;
	private final UserRepository userRepository;
	private final RestTemplate restTemplate;
	
	@Value("${app.python.base-url}")
	private String pythonBaseUrl;
	
	@Value("${app.upload.base-path}")
    private String uploadBasePath;


	/*-------------------------------------------------------------------
	  						Question Paper
	 -------------------------------------------------------------------*/
	/**
     * Creates a new question paper for an exam.
     * Each exam can have multiple question papers with different set labels (Set A, Set B).
     * Throws if set label already exists for the same exam.
     *
     * @param request   validated QuestionPaperRequestDTO
     * @param facultyId ID of the faculty submitting (from JWT)
     * @param file      uploaded question paper PDF
     * @return saved QuestionPaperEntity
	 * @throws BadRequestException 
     */
	public QuestionPaperEntity createQuestionPaper(
			QuestionPaperRequestDTO requestDTO,
			String facultyId,
			MultipartFile file
	) throws BadRequestException {
		
		//step-1: verify exam exists
		ExamEntity exam = examRepository.findById(requestDTO.getExamId())
					.orElseThrow(()-> new BadRequestException("EXAM_NOT_FOUND"));
		
		//step-2: verify faculty exists
		UserEntity faculty = userRepository.findById(facultyId)
					.orElseThrow(()->new BadRequestException("FACULTY_NOT_FOUND"));
		
		//step-3: check duplicate set label exists for same exam
		if (questionPaperRepository.existsByExamAndSetLable(exam, requestDTO.getSetLable())) {
			throw new BadRequestException("SET_LABEL_EXISTS");
		}
		
		try {
			// step-4: save the question paper
			String paperDir = uploadBasePath + File.separator + "question_papers"+File.separator+requestDTO.getExamId();
			Files.createDirectories(Paths.get(paperDir));
			String filePath = paperDir + File.separator + requestDTO.getSetLable().replace(" ", "-")+".pdf";
			Files.write(Paths.get(filePath),file.getBytes());
			
			// Step 5 — Create and save QuestionPaper record
			QuestionPaperEntity questionPaper = new QuestionPaperEntity();
			questionPaper.setExam(exam);
			questionPaper.setFaculty(faculty);
			questionPaper.setFilePath(filePath);
			questionPaper.setSetLable(requestDTO.getSetLable());
			questionPaper.setTitle(requestDTO.getTitle());
			questionPaper.setStatus(QuestionPaperStatus.DRAFT);
			
			return questionPaperRepository.save(questionPaper);
			
		} catch (Exception e) {
			throw new BadRequestException("FILE_SAVE_FAILED: " + e.getMessage()); 
		}
	}
	
	/*-----------------------------------------------------
	 				Question
	------------------------------------------------------*/
	/**
     * Creates a new question under a question paper.
     * Question numbers must be unique within the same paper.
     *
     * @param request validated QuestionRequestDTO
     * @return saved QuestionEntity
	 * @throws BadRequestException 
     */
	public QuestionEntity createQuestion(
			QuestionRequestDTO requestDTO
	) throws BadRequestException {
		
		//step-1: verify question paper exists
		QuestionPaperEntity questionPaper = questionPaperRepository.findById(requestDTO.getQuestionPaperId())
				.orElseThrow(()-> new BadRequestException("QUESTION_PAPER_NOT_FOUND"));
		
		// Step 2 — Check duplicate question number in same paper
		if (questionRepository.existsByQuestionNumberAndQuestionPaper(requestDTO.getQuestionNumber(), questionPaper)) {
			throw new BadRequestException("QUESTION_NUMBER_ALREADY_EXISTS");
		}	
		
		//step-3 create and save question
		QuestionEntity question = new QuestionEntity();
		question.setQuestionNumber(requestDTO.getQuestionNumber());
		question.setQuestionPaper(questionPaper);
		question.setTotalMarks(requestDTO.getTotalMarks());
		return questionRepository.save(question);
	}
	
	/*-----------------------------------------------------
						Sub-Question
	------------------------------------------------------*/
	/**
     * Creates a sub-question under a parent question.
     * Sub-question labels must be unique within the same question (1a, 1b, 1c).
     *
     * @param request validated SubQuestionRequestDTO
     * @return saved SubQuestionEntity
	 * @throws BadRequestException 
     */
	public SubQuestionEntity createSubQuestion(
			SubQuestionRequestDTO requestDTO
	) throws BadRequestException {
		
		//step-1: verify the question exists
		QuestionEntity question = questionRepository.findById(requestDTO.getQuestionId())
					.orElseThrow(()->new BadRequestException("QUESTION_NOT_FOUND"));
		
		//step-3: verify if the duplicate sub question exists
		if (subQuestionRepository.existsBySubQuestionLabelAndQuestion(requestDTO.getSubQuestionLabel(),question)) {
			throw new BadRequestException("LABLE_ALREADY_EXISTS");
		}
		
		// Step 3 — Create and save sub-question
		SubQuestionEntity subQuestion = new SubQuestionEntity();
		subQuestion.setQuestion(question);
		subQuestion.setQuestionText(requestDTO.getQuestionText());
		subQuestion.setMarks(requestDTO.getMarks());
		subQuestion.setSubQuestionLabel(requestDTO.getSubQuestionLabel());
		return subQuestionRepository.save(subQuestion);
	}
	
	/*------------------------------------------------------------
	  						Model answer
	 ------------------------------------------------------------*/
	/**
     * Submits a model answer for a sub-question.
     *
     * Flow:
     * 1. Verify sub-question exists
     * 2. Check no model answer already exists for this sub-question
     * 3. Save model answer to DB (embedding = null for now)
     * 4. Call Python /embeddings/generate to get the 384-dim embedding
     * 5. Update model answer with embedding
     *
     * Note: If Python is unavailable, model answer is saved without embedding.
     * Embedding can be regenerated later. This prevents blocking faculty workflow.
     *
     * @param request   validated ModelAnswerRequestDTO
     * @param facultyId ID of submitting faculty (from JWT)
     * @return saved ModelAnswerEntity with embedding
	 * @throws BadRequestException 
     */
	public ModelAnswerEntity createModelAnswer(
			ModelAnswerRequestDTO requestDTO,
			String facultyId
	) throws BadRequestException {
		
		//step-1: verify sub question exists
		SubQuestionEntity subQuestion = subQuestionRepository.findById(requestDTO.getSubQuestionId())
					.orElseThrow(()-> new BadRequestException("SUBQUESTION_NOT_FOUND"));
		
		//step-2: verify faculty exists
		UserEntity faculty = userRepository.findById(facultyId)
						.orElseThrow(()-> new BadRequestException("FACULTY_NOT_FOUND"));
		
		//step-3: check if the model answer already exists for this sub question
		if (modelAnswerRepository.existsBySubQuestion(subQuestion)) {
			throw new BadRequestException("MODEL_ANSWER_EXISTS");
		}
		
		//step-4: create the model answer entity
		ModelAnswerEntity modelAnswer = new ModelAnswerEntity();
		modelAnswer.setAnswerText(requestDTO.getAnswerText());
		modelAnswer.setSubQuestion(subQuestion);
		modelAnswer.setKeyConcepts(requestDTO.getKeyConcepts());
		modelAnswer.setUser(faculty);
		
		ModelAnswerEntity savedModelAnswer = modelAnswerRepository.save(modelAnswer);
		
		try {
			
			// Step 5 — Call Python to generate embedding
			float[] embeddings = generateEmbeddings(requestDTO.getAnswerText());
			savedModelAnswer.setEmbedding(embeddings);
			savedModelAnswer = modelAnswerRepository.save(savedModelAnswer);
			return savedModelAnswer;
			
			
		} catch (Exception e) {
			 // Python unavailable — save without embedding, log warning
            // Embedding can be regenerated later via a separate endpoint
            System.err.println("WARNING: Embedding generation failed for sub-question "
                    + requestDTO.getSubQuestionId() + ": " + e.getMessage());
		}
		return savedModelAnswer;
	}
	
	/*----------------------------------------------------------------
	 						Grievance review
	-----------------------------------------------------------------*/
	/**
     * Returns all grievances pending faculty review.
     * Faculty sees both SYSTEM_GENERATED (OCR failed) and STUDENT_RAISED grievances.
     *
     * @param facultyId ID of the faculty (from JWT) — for future filtering by subject
     * @return list of pending grievances
	 * @throws BadRequestException 
     */
	public List<GrievanceEntity> allPendingGrievance(String facultyId) throws BadRequestException{
		
		//step-1: check faculty exists
		UserEntity faculty = userRepository.findById(facultyId)
					.orElseThrow(()->new BadRequestException("FACULTY_NOT_FOUND"));
		
		return grievanceRepository.findByStatus(GrievanceStatus.PENDING);
	}
	
	/**
     * Faculty reviews and resolves a grievance.
     *
     * Flow:
     * 1. Fetch grievance
     * 2. Update awarded_marks on the Result
     * 3. Mark result as overridden
     * 4. Update grievance status to RESOLVED or REJECTED
     *
     * @param grievanceId ID of the grievance being reviewed
     * @param request     review decision with awarded_marks and comment
     * @param facultyId   ID of the reviewing faculty (from JWT)
     * @return updated GrievanceEntity
	 * @throws BadRequestException 
     */
	@Transactional
	public GrievanceEntity grievanceReview(
			String grievanceId,
			GrievanceReviewRequest request,
			String facultyId
	) throws BadRequestException {
		// Step 1 — Fetch grievance
		GrievanceEntity grievance = grievanceRepository.findById(grievanceId)
				.orElseThrow(()-> new BadRequestException("GRIEVANCE_NOT_FOUND"));
		
		// step-2: check faculty exists
		UserEntity faculty = userRepository.findById(facultyId)
					.orElseThrow(()-> new BadRequestException("FACULTY_NOT_EXISTS"));
		
		// Step 3 — Only PENDING or UNDER_REVIEW grievances can be reviewed
		if (grievance.getStatus() == GrievanceStatus.RESOLVED 
				|| grievance.getStatus() == GrievanceStatus.REJECTED) {
			throw new RuntimeException("GRIEVANCE_ALREADY_CLOSED");			
		}
		
		// Step 4 — Update the Result with new marks if RESOLVED
		ResultEntity result = grievance.getResult();
		if (request.getStatus() == GrievanceStatus.RESOLVED) {
            result.setFinalMarks(request.getAwardedMarks());
            result.setIsOverriden(true);
            result.setOverridenBy(faculty);
            result.setOverrideReason(request.getReviewerComment());
            resultRepository.save(result);
        }
		
		// Step 5 — Update grievance
        grievance.setAwardedMarks(request.getAwardedMarks());
        grievance.setReviewerComment(request.getReviewerComment());
        grievance.setStatus(request.getStatus());
        grievance.setReviewedBy(faculty);
        grievance.setResolvedAt(LocalDateTime.now());

        return grievanceRepository.save(grievance);
	}
	
	
	
	/*---------------------------------------------------------------------
	 						Helper method
	 -------------------------------------------------------------------*/
	 /**
     * Calls Python FastAPI /embeddings/generate endpoint to get
     * a 384-dimensional vector embedding for the given text.
     *
     * @param text the model answer text to embed
     * @return float array of 384 dimensions
     */
	private float[] generateEmbeddings(String text) {
		String url = pythonBaseUrl + "/embeddings/generate";
		
		Map<String, String> requestBody = new HashMap<>();
		
		requestBody.put("text", text);
		
		ResponseEntity<Map> response = restTemplate.postForEntity(url, requestBody, Map.class);
		
		if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
			List<Double> embeddingList = (List<Double>) response.getBody().get("embedding");
			float[] embedding = new float[embeddingList.size()];
			
			for (int i = 0; i < embeddingList.size(); i++) {
				embedding[i] = embeddingList.get(i).floatValue();				
			}
			
			return embedding;
		}
		
		throw new RuntimeException("EMBEDDING_GENERATION_FAILED");
	}
}
