package com.evalai.main.controllers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.evalai.main.entities.*;
import org.apache.coyote.BadRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.evalai.main.dtos.request.GrievanceReviewRequest;
import com.evalai.main.dtos.request.ModelAnswerRequestDTO;
import com.evalai.main.dtos.request.QuestionPaperRequestDTO;
import com.evalai.main.dtos.request.QuestionRequestDTO;
import com.evalai.main.dtos.request.SubQuestionRequestDTO;
import com.evalai.main.dtos.response.GrievanceResponseDTO;
import com.evalai.main.dtos.response.QuestionPaperResponseDTO;
import com.evalai.main.services.FacultyService;
import com.evalai.main.utils.JwtUtils;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Handles all Faculty-facing HTTP endpoints.
 * All endpoints require FACULTY role — enforced via Spring Security.
 *
 * Endpoints:
 * POST /faculty/question-papers          → Upload question paper PDF
 * POST /faculty/questions                → Create question under paper
 * POST /faculty/sub-questions            → Create sub-question under question
 * POST /faculty/model-answers            → Submit model answer + trigger embedding
 * GET  /faculty/grievances               → View pending grievances
 * PUT  /faculty/grievances/{id}/review   → Resolve or reject grievance
 *
 * @author Vaibhav Sutar
 * @version 1.0
 */

@RestController
@RequestMapping("/faculty")
@PreAuthorize("hasRole('FACULTY')")
@RequiredArgsConstructor
public class FacultyController {
	private final FacultyService facultyService;
	private final JwtUtils jwtUtils;

	
	/*----------------------------------------------------
	  					Question paper
	 -----------------------------------------------------*/
	 /**
     * Uploads a question paper PDF for an exam.
     * Request is multipart/form-data.
     *
     * @return 201 CREATED with question paper details
     *         404 NOT FOUND if exam not found
     *         409 CONFLICT if set label already exists for this exam
	 * @throws BadRequestException 
     */
	@PostMapping(value = "/question-paper", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<?> createQuestionPaper(
			@RequestParam("title") String title,
		    @RequestParam("examId") String examId,
            @RequestParam("subjectId") String subjectId,
		    @RequestParam("setLable") String setLable,
			@RequestPart("file") MultipartFile file,
			@RequestHeader("Authorization") String authHeader
	) throws BadRequestException{
		try {
			String facultyId = jwtUtils.extractUserId(authHeader.substring(7));
			
			QuestionPaperRequestDTO request = new QuestionPaperRequestDTO();
	        request.setTitle(title);
	        request.setExamId(examId);
	        request.setSetLable(setLable);
            request.setSubjectId(subjectId);
			
			QuestionPaperEntity saved = facultyService.createQuestionPaper(request, facultyId,subjectId,file);
			
			QuestionPaperResponseDTO responseDTO = new QuestionPaperResponseDTO();
			responseDTO.setQuestionPaperId(saved.getId());
			responseDTO.setExamId(saved.getExam().getId());
			responseDTO.setExamTitle(saved.getExam().getTitle());
			responseDTO.setSetLabel(saved.getSetLable());
			responseDTO.setStatus(saved.getStatus());
			responseDTO.setCreatedAt(saved.getCreateAt());
			responseDTO.setTitle(saved.getTitle());
			
			 return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
		} catch (RuntimeException  e) {
			if ("EXAM_NOT_FOUND".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Exam not found");
            }
			if ("FACULTY_NOT_FOUND".equals(e.getMessage())) {
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Faculty not Found");
			}
			
            if ("SET_LABEL_EXISTS".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("A question paper with this set label already exists for this exam");
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to create question paper: " + e.getMessage());
		}
	}
	
	/*----------------------------------------------------
	 					Question
	-----------------------------------------------------*/
	/**
     * Creates a question under a question paper.
     *
     * @return 201 CREATED with question details
     *         404 NOT FOUND if question paper not found
     *         409 CONFLICT if question number already exists in this paper
	 * @throws BadRequestException 
     */
	@PostMapping("/questions")
	public ResponseEntity<?> createQuestion(
			@Valid @RequestBody QuestionRequestDTO requestDTO
	) throws BadRequestException{
		try {
			QuestionEntity question = facultyService.createQuestion(requestDTO);
			
			Map<String, Object> response = new HashMap<>();
			
			response.put("questionId", question.getId());
			response.put("questionNumber", question.getQuestionNumber());
			response.put("totalMarks",question.getTotalMarks());
			response.put("questionPaperId", question.getQuestionPaper().getId());
			response.put("createdAt", question.getCreatedAt());
			
			return ResponseEntity.status(HttpStatus.CREATED).body(response);
		} catch (RuntimeException e) {
			if("QUESTION_PAPER_NOT_FOUND".equals(e.getMessage())) {
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Question paper with "+requestDTO.getQuestionPaperId()+" not found");
			}
			if ("QUESTION_NUMBER_ALREADY_EXISTS".equals(e.getMessage())) {
				return ResponseEntity.status(HttpStatus.CONFLICT).body("Question number "+requestDTO.getQuestionNumber()+" already exists");
			}
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
		}
	}
	
	/*------------------------------------------------------------------
	 						Sub question
	--------------------------------------------------------------------*/
	/**
     * Creates a sub-question under a parent question.
     *
     * @return 201 CREATED with sub-question details
     *         404 NOT FOUND if question not found
     *         409 CONFLICT if label already exists under this question
	 * @throws BadRequestException 
     */
	@PostMapping("/sub-questions")
	public ResponseEntity<?> createSubQuestion(
			@Valid @RequestBody SubQuestionRequestDTO requestDTO
	) throws BadRequestException{
		try {
			SubQuestionEntity saved = facultyService.createSubQuestion(requestDTO);
			
			Map<String, Object> response = new HashMap<>();
			 response.put("subQuestionId", saved.getId());
	         response.put("subQuestionLabel", saved.getSubQuestionLabel());
	         response.put("questionText", saved.getQuestionText());
	         response.put("marks", saved.getMarks());
	         response.put("questionId", saved.getQuestion().getId());
	         response.put("createdAt", saved.getCreatedAt());
	            
	         return ResponseEntity.status(HttpStatus.CREATED).body(response);
		} catch (RuntimeException e) {
			if ("QUESTION_NOT_FOUND".equals(e.getMessage())) {
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Question Id: "+requestDTO.getQuestionId()+" Not found");
			}
			
			if ("LABLE_ALREADY_EXISTS".equals(e.getMessage())) {
				return ResponseEntity.status(HttpStatus.CONFLICT).body("Question lable "+requestDTO.getSubQuestionLabel()+" already exists");
			}
			
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
		}
	}
	
	/*--------------------------------------------------------------
	  						Model answer
	---------------------------------------------------------------*/
	/**
     * Submits a model answer for a sub-question.
     * Triggers Python embedding generation automatically.
     *
     * Note: If Python service is unavailable, model answer is still saved.
     * Embedding will be null until Python is available.
     *
     * @return 201 CREATED with model answer details
     *         404 NOT FOUND if sub-question not found
     *         409 CONFLICT if model answer already exists for this sub-question
	 * @throws BadRequestException 
     */
	@PostMapping("/model-answers")
	public ResponseEntity<?> createModelAnswer(
			@Valid @RequestBody ModelAnswerRequestDTO requestDTO,
			@RequestHeader("Authorization") String authHeader
	) throws BadRequestException{
		try {
			String facultyId = jwtUtils.extractUserId(authHeader.substring(7));
			ModelAnswerEntity modelAnswer = facultyService.createModelAnswer(requestDTO, facultyId);
			
			Map<String, Object> response = new HashMap<>();
			response.put("modelAnswerId", modelAnswer.getId());
	        response.put("subQuestionId", modelAnswer.getSubQuestion().getId());
	        response.put("subQuestionLabel", modelAnswer.getSubQuestion().getSubQuestionLabel());
	        response.put("answerText", modelAnswer.getAnswerText());
	        response.put("keyConcepts", modelAnswer.getKeyConcepts());
	        response.put("embeddingGenerated", modelAnswer.getEmbedding() != null);
	        response.put("createdAt", modelAnswer.getCreateAt());
			
			return ResponseEntity.status(HttpStatus.CREATED).body(response);
		}catch (RuntimeException e) {
			// TODO: handle exception
			if ("SUBQUESTION_NOT_FOUND".equals(e.getMessage())) {
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Sub Question Id: "+requestDTO.getSubQuestionId()+" Not found");
			}
			if ("FACULTY_NOT_FOUND".equals(e.getMessage())) {
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Faculty Not found");
			}
			if ("MODEL_ANSWER_EXISTS".equals(e.getMessage())) {
				return ResponseEntity.status(HttpStatus.CONFLICT).body("Model Answer already exists");
			}
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
		}
	}
	
	/*--------------------------------------------------------
	 						Grievances
	 --------------------------------------------------------*/
	/**
     * Returns all grievances pending faculty review.
     *
     * @return 200 OK with list of pending grievances
     */
	@GetMapping("/grievances")
	public ResponseEntity<?> getPendingGrievances(
			@RequestHeader("Authorization") String authHeader
	){
		try {
			String facultyId = jwtUtils.extractUserId(authHeader.substring(7));
			List<GrievanceEntity> grievances = facultyService.allPendingGrievance(facultyId);
			
			List<GrievanceResponseDTO> response = grievances.stream().map(g->{
				GrievanceResponseDTO dto = new GrievanceResponseDTO();
				dto.setGrievanceId(g.getId());
				dto.setStudentName(g.getStudent().getName());
                dto.setStudentId(g.getStudent().getId());
                dto.setSubQuestionLabel(
                    g.getResult().getSubQuestion().getSubQuestionLabel()
                );
                dto.setGrievanceType(g.getGrievanceType());
                dto.setStatus(g.getStatus());
                dto.setStudentReason(g.getStudentReason());
                dto.setFailureReason(g.getFailureReason());
                dto.setRequestedMarks(g.getRequestedMarks());
                dto.setAwardedMarks(g.getAwardedMarks());
                dto.setAiMarks(g.getResult().getAiMarks());
                dto.setReviewerComment(g.getReviewerComment());
                dto.setRaisedAt(g.getRaisedAt());
                dto.setResolvedAt(g.getResolvedAt());
                return dto;
			}).toList();
			
			return ResponseEntity.status(HttpStatus.OK).body(response);
		}catch (Exception e) {
			
			if ("FACULTY_NOT_FOUND".equals(e.getMessage())) {
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Faculty not found");
			}
			
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
		}
	}
	
	/**
     * Faculty reviews a grievance — resolves or rejects it.
     * On RESOLVED — updates final_marks on the Result record.
     * On REJECTED — keeps original ai_marks as final_marks.
     *
     * @param grievanceId ID of the grievance to review
     * @param request     review decision with awarded marks and comment
     * @return 200 OK with updated grievance details
     *         404 NOT FOUND if grievance not found
     *         400 BAD REQUEST if grievance already closed
	 * @throws BadRequestException 
     */
    @PutMapping("/grievances/{grievanceId}/review")
    public ResponseEntity<?> reviewGrievance(
            @PathVariable String grievanceId,
            @Valid @RequestBody GrievanceReviewRequest request,
            @RequestHeader("Authorization") String authHeader
    ) throws BadRequestException {
        try {
            String facultyId = jwtUtils.extractUserId(authHeader.substring(7));

            GrievanceEntity updated = facultyService.grievanceReview(
                    grievanceId, request, facultyId
            );

            GrievanceResponseDTO response = new GrievanceResponseDTO();
            response.setGrievanceId(updated.getId());
            response.setStudentName(updated.getStudent().getName());
            response.setStudentId(updated.getStudent().getId());
            response.setSubQuestionLabel(
                updated.getResult().getSubQuestion().getSubQuestionLabel()
            );
            response.setGrievanceType(updated.getGrievanceType());
            response.setStatus(updated.getStatus());
            response.setStudentReason(updated.getStudentReason());
            response.setAwardedMarks(updated.getAwardedMarks());
            response.setReviewerComment(updated.getReviewerComment());
            response.setRaisedAt(updated.getRaisedAt());
            response.setResolvedAt(updated.getResolvedAt());

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            if ("GRIEVANCE_NOT_FOUND".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Grievance not found");
            }
            if ("GRIEVANCE_ALREADY_CLOSED".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Grievance is already resolved or rejected");
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to review grievance: " + e.getMessage());
        }
    }
}
