package com.evalai.main.services;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.coyote.BadRequestException;
import org.springframework.stereotype.Service;

import com.evalai.main.dtos.request.GrievanceRequestDTO;
import com.evalai.main.dtos.response.StudentFeedbackResponseDTO;
import com.evalai.main.dtos.response.StudentGrievanceResponseDTO;
import com.evalai.main.dtos.response.StudentResultResponseDTO;
import com.evalai.main.entities.AnswersheetEntity;
import com.evalai.main.entities.ExamEntity;
import com.evalai.main.entities.FeedbackEntity;
import com.evalai.main.entities.GrievanceEntity;
import com.evalai.main.entities.ResultEntity;
import com.evalai.main.entities.UserEntity;
import com.evalai.main.enums.EvaluationStatus;
import com.evalai.main.enums.GrievanceStatus;
import com.evalai.main.enums.GrievanceType;
import com.evalai.main.repositories.AnswersheetRepository;
import com.evalai.main.repositories.ExamRepository;
import com.evalai.main.repositories.FeedbackRepository;
import com.evalai.main.repositories.GrievanceRepository;
import com.evalai.main.repositories.ResultRepository;
import com.evalai.main.repositories.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Handles all Student business logic:
 * 1. View results for an exam (only their own)
 * 2. View AI-generated feedback for an exam
 * 3. Raise a grievance for a sub-question result
 * 4. Track all grievances raised by the student
 *
 * Security: Student ID is always extracted from JWT — never trusted from request params.
 *
 * @author Vaibhav Sutar
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
public class StudentService {
	private final AnswersheetRepository answersheetRepository;
	private final ResultRepository resultRepository;
	private final GrievanceRepository grievanceRepository;
	private final ExamRepository examRepository;
	private final UserRepository userRepository;
	private final FeedbackRepository feedbackRepository;
	
	/*-----------------------------------------------------
	 						RESULT
	------------------------------------------------------*/
	/**
     * Returns the evaluated results for a student for a specific exam.
     *
     * Security rules enforced here:
     * - studentId comes from JWT, never from request
     * - Results only visible when evaluation is COMPLETED or COMPLETED_WITH_FAILURES
     * - Only final_marks returned — ai_marks never exposed to student
     *
     * @param examId    ID of the exam
     * @param studentId extracted from JWT — guaranteed to be the logged-in student
     * @return StudentResultResponseDTO with per-sub-question breakdown
	 * @throws BadRequestException 
     */
public StudentResultResponseDTO getResult(String examId, String studentId) throws BadRequestException {
        
        ExamEntity exam = examRepository.findById(examId)
                    .orElseThrow(() -> new BadRequestException("EXAM_NOT_FOUND"));
        
        UserEntity student = userRepository.findById(studentId)
                    .orElseThrow(() -> new BadRequestException("STUDENT_NOT_FOUND"));
        
        AnswersheetEntity answersheet = answersheetRepository.findByExamAndStudent(exam, student)
                            .orElseThrow(() -> new BadRequestException("ANSWER_SHEET_NOT_FOUND"));
        
        // Updated to match "RESULTS_NOT_READY" case in helper
        if (answersheet.getEvaluationStatus() != EvaluationStatus.COMPLETED &&
            answersheet.getEvaluationStatus() != EvaluationStatus.COMPLETED_WITH_FAILURES) {
            throw new RuntimeException("RESULTS_NOT_READY");
        }
        
        List<ResultEntity> allResults = resultRepository.findByAnswersheet(answersheet);
        
        StudentResultResponseDTO responseDTO = new StudentResultResponseDTO();
        responseDTO.setExamTitle(exam.getTitle());
        responseDTO.setSubjectName(exam.getSubject().getName());
        responseDTO.setAcademicYear(exam.getAcademicYear());
        responseDTO.setEvaluationStatus(answersheet.getEvaluationStatus());
        
        List<StudentResultResponseDTO.SubQuestionResultDTO> subResult = allResults.stream()
                .map(result -> {
                    StudentResultResponseDTO.SubQuestionResultDTO dto = new StudentResultResponseDTO.SubQuestionResultDTO();
                    dto.setResultId(result.getId());
                    dto.setSubQuestionLable(result.getSubQuestion().getSubQuestionLabel());
                    dto.setSubQuestionText(result.getSubQuestion().getQuestionText());
                    dto.setMaxMarks(result.getTotalMarks());
                    dto.setFinalMarks(result.getFinalMarks());
                    dto.setIsOverridden(result.getIsOverriden());
                    dto.setStatus(result.getStatus());
                    return dto;
                }).toList();
        
        responseDTO.setResult(subResult);
        responseDTO.setTotalMarks(Float.valueOf(exam.getTotalMarks()));
        
        float obtainedMarks = (float)allResults.stream()
                .mapToDouble(r -> r.getFinalMarks() != null ? r.getFinalMarks() : 0.0).sum();
        responseDTO.setObtainedMarks(obtainedMarks);
        
        return responseDTO;
    }
	
	/*------------------------------------------------------------
	 						FEEDBACK
	-------------------------------------------------------------*/
	/**
     * Returns AI-generated feedback for a student for a specific exam.
     * Only available after evaluation is complete.
     *
     * @param examId    ID of the exam
     * @param studentId extracted from JWT
     * @return StudentFeedbackResponseDTO with per-sub-question feedback
	 * @throws BadRequestException 
     */
	public StudentFeedbackResponseDTO getFeedback(String examId, String studentId) throws BadRequestException {
	    
	    ExamEntity exam = examRepository.findById(examId)
	                .orElseThrow(() -> new BadRequestException("EXAM_NOT_FOUND"));
	    
	    UserEntity student = userRepository.findById(studentId)
	            .orElseThrow(() -> new BadRequestException("STUDENT_NOT_FOUND"));
	    
	    AnswersheetEntity answersheet = answersheetRepository.findByExamAndStudent(exam, student)
	                .orElseThrow(() -> new BadRequestException("ANSWER_SHEET_NOT_FOUND"));
	    
	    // Updated to match "ANSWERSHEET_NOT_READY" case in helper
	    if (answersheet.getEvaluationStatus() != EvaluationStatus.COMPLETED &&
	        answersheet.getEvaluationStatus() != EvaluationStatus.COMPLETED_WITH_FAILURES) {
	        throw new RuntimeException("ANSWERSHEET_NOT_READY");
	    }
	    
	    List<FeedbackEntity> allFeedbacks = feedbackRepository.findByAnswersheet(answersheet);
	    
	    // Updated to match "FEEDBACK_NOT_AVAILABLE" case in helper
	    if (allFeedbacks.isEmpty()) {
	        throw new BadRequestException("FEEDBACK_NOT_AVAILABLE");
	    }
	    
	    StudentFeedbackResponseDTO responseDTO = new StudentFeedbackResponseDTO();
	    responseDTO.setExamTitle(exam.getTitle());
	    responseDTO.setSubjectName(exam.getSubject().getName());
	    
	    List<StudentFeedbackResponseDTO.SubQuestionFeedbackDTO> feedbackDTOs = allFeedbacks.stream()
	            .map(feedback -> {
	                 StudentFeedbackResponseDTO.SubQuestionFeedbackDTO dto = new StudentFeedbackResponseDTO.SubQuestionFeedbackDTO();
	                 dto.setSubQuestionLabel(feedback.getResult().getSubQuestion().getSubQuestionLabel());
	                 dto.setStrengths(feedback.getStrengths());
	                 dto.setWeakness(feedback.getWeakness());
	                 dto.setSuggestions(feedback.getSuggestions());
	                 dto.setOverallFeedback(feedback.getOverallFeedback());
	                 dto.setKeyConceptsMissed(feedback.getKeyConceptsMissed());
	                 return dto;
	            }).toList();
	            
	    responseDTO.setFeedbacks(feedbackDTOs);
	    return responseDTO;
	}
	
	/*-----------------------------------------------------------
	 						RAISE GRIEVANCE
	------------------------------------------------------------*/
	/**
     * Student raises a grievance for a specific sub-question result.
     *
     * Business rules enforced:
     * 1. Result must belong to the requesting student — prevents raising on others' results
     * 2. One grievance per result — cannot raise twice for same sub-question
     * 3. Must be within grievance_deadline — checked against exam deadline
     * 4. Evaluation must be complete before grievance can be raised
     *
     * @param request   GrievanceRequestDTO with resultId, reason, requestedMarks
     * @param studentId extracted from JWT
     * @return saved GrievanceEntity
	 * @throws BadRequestException 
     */
	public GrievanceEntity raiseGrievance(GrievanceRequestDTO requestDTO, String studentId) throws BadRequestException {
        
        UserEntity student = userRepository.findById(studentId)
                .orElseThrow(() -> new BadRequestException("STUDENT_NOT_FOUND"));
        
        // Using RESULT_NOT_FOUND to be specific, though helper uses RESULTS_NOT_READY in your snippet
        // I recommend using "RESULT_NOT_FOUND" for findById failures
        ResultEntity result = resultRepository.findById(requestDTO.getResultId())
                        .orElseThrow(() -> new BadRequestException("RESULT_NOT_FOUND"));
        
        // Match "UNAUTHORIZED_GRIEVANCE"
        if (!result.getAnswersheet().getStudent().getId().equals(studentId)) {
            throw new RuntimeException("UNAUTHORIZED_GRIEVANCE");
        }
        
        ExamEntity exam = result.getAnswersheet().getExam();
        
        // Match "GRIEVANCE_DEADLINE_PASSED"
        if (exam.getGrievanceDeadline() != null && LocalDateTime.now().isAfter(exam.getGrievanceDeadline())) {
            throw new BadRequestException("GRIEVANCE_DEADLINE_PASSED");
        }
        
        // Match "RESULTS_NOT_READY"
        AnswersheetEntity answersheet = result.getAnswersheet();
        if (answersheet.getEvaluationStatus() != EvaluationStatus.COMPLETED &&
            answersheet.getEvaluationStatus() != EvaluationStatus.COMPLETED_WITH_FAILURES) {
            throw new BadRequestException("RESULTS_NOT_READY");
        }
        
        // Match "GRIEVANCE_ALREADY_EXISTS"
        if (grievanceRepository.existsByResultAndGrievanceType(result, GrievanceType.STUDENT_RAISED)) {
            throw new BadRequestException("GRIEVANCE_ALREADY_EXISTS");
        }
        
        GrievanceEntity grievance = new GrievanceEntity();
        grievance.setResult(result);
        grievance.setStudent(student);
        grievance.setGrievanceType(GrievanceType.STUDENT_RAISED);
        grievance.setStudentReason(requestDTO.getReason());
        grievance.setRequestedMarks(requestDTO.getRequestedMarks());
        grievance.setStatus(GrievanceStatus.PENDING);

        return grievanceRepository.save(grievance);
    }
	
	/*------------------------------------------------------------------
	 						TRACK GRIEVANCE
	------------------------------------------------------------------*/
	 /**
     * Returns all grievances raised by the student across all exams.
     * Student can only see their own grievances — enforced by querying with studentId from JWT.
     *
     * @param studentId extracted from JWT
     * @return list of StudentGrievanceResponseDTO
	 * @throws BadRequestException 
     */
	public List<StudentGrievanceResponseDTO> getMyGrievances(String studentId) throws BadRequestException {

        UserEntity student = userRepository.findById(studentId)
                .orElseThrow(() -> new BadRequestException("STUDENT_NOT_FOUND"));

        List<GrievanceEntity> grievances = grievanceRepository.findByStudent(student);

        return grievances.stream().map(grievance -> {
            StudentGrievanceResponseDTO dto = new StudentGrievanceResponseDTO();
            dto.setGrievanceId(grievance.getId());
            dto.setSubQuestionLabel(grievance.getResult().getSubQuestion().getSubQuestionLabel());
            dto.setGrievanceType(grievance.getGrievanceType());
            dto.setStatus(grievance.getStatus());
            dto.setStudentReason(grievance.getStudentReason());
            dto.setRequestedMarks(grievance.getRequestedMarks());
            dto.setAwardedMarks(grievance.getAwardedMarks());
            dto.setAiMarks(grievance.getResult().getAiMarks());
            dto.setFinalMarks(grievance.getResult().getFinalMarks());
            dto.setReviewerComment(grievance.getReviewerComment());
            dto.setRaisedAt(grievance.getRaisedAt());
            dto.setResolvedAt(grievance.getResolvedAt());
            return dto;
        }).toList();
    }
}
