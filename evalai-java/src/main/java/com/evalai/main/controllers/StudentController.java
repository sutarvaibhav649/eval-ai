package com.evalai.main.controllers;

import java.util.List;

import org.apache.coyote.BadRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.evalai.main.dtos.request.GrievanceRequestDTO;
import com.evalai.main.dtos.response.StudentFeedbackResponseDTO;
import com.evalai.main.dtos.response.StudentGrievanceResponseDTO;
import com.evalai.main.dtos.response.StudentResultResponseDTO;
import com.evalai.main.entities.GrievanceEntity;
import com.evalai.main.services.StudentService;
import com.evalai.main.utils.JwtUtils;

import jakarta.validation.Valid;


/**
 * Handles all Student-facing HTTP endpoints.
 * All endpoints require STUDENT role — enforced via Spring Security.
 *
 * Security: studentId is ALWAYS extracted from JWT.
 * It is NEVER accepted as a request parameter or path variable.
 * This guarantees a student can only access their own data.
 *
 * Endpoints:
 * GET  /student/results/{examId}    → View results for an exam
 * GET  /student/feedback/{examId}   → View AI feedback for an exam
 * POST /student/grievances          → Raise a grievance
 * GET  /student/grievances          → Track all raised grievances
 *
 * @author Vaibhav Sutar
 * @version 1.0
 */
@RestController
@RequestMapping("/student")
@PreAuthorize("hasRole('STUDENT')")
public class StudentController {

    private final StudentService studentService;
    private final JwtUtils jwtUtils;

    public StudentController(StudentService studentService, JwtUtils jwtUtils) {
        this.studentService = studentService;
        this.jwtUtils = jwtUtils;
    }

    /*---------------------------------------------------------- 
     							RESULTS
    ------------------------------------------------------------*/ 

    /**
     * Returns evaluated results for the logged-in student for a specific exam.
     *
     * Security:
     * - studentId extracted from JWT — student cannot spoof another student's ID
     * - Results blocked if evaluation not yet complete
     * - Only final_marks returned — ai_marks never exposed
     *
     * @param examId     path variable — which exam's results to fetch
     * @param authHeader Bearer token from Authorization header
     * @return 200 OK with full result breakdown per sub-question
     *         404 NOT FOUND if exam or answer sheet not found
     *         403 FORBIDDEN if evaluation not complete yet
     * @throws BadRequestException 
     */
    @GetMapping("/results/{examId}")
    public ResponseEntity<?> getResults(
            @PathVariable String examId,
            @RequestHeader("Authorization") String authHeader
    ) throws BadRequestException {
        try {
            // studentId always comes from JWT — never from request
            String studentId = jwtUtils.extractUserId(authHeader.substring(7));

            StudentResultResponseDTO response = studentService.getResult(examId, studentId);
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            return handleStudentException(e);
        }
    }

    /*---------------------------------------------------------- 
							FEEDBACK
	------------------------------------------------------------*/ 

    /**
     * Returns AI-generated feedback for the logged-in student for a specific exam.
     * Only available after evaluation is complete.
     *
     * @param examId     path variable — which exam's feedback to fetch
     * @param authHeader Bearer token from Authorization header
     * @return 200 OK with per-sub-question feedback including strengths,
     *         weaknesses, suggestions, and key concepts missed
     *         404 NOT FOUND if exam, answer sheet, or feedback not found
     *         403 FORBIDDEN if evaluation not complete yet
     * @throws BadRequestException 
     */
    @GetMapping("/feedback/{examId}")
    public ResponseEntity<?> getFeedback(
            @PathVariable String examId,
            @RequestHeader("Authorization") String authHeader
    ) throws BadRequestException {
        try {
            String studentId = jwtUtils.extractUserId(authHeader.substring(7));

            StudentFeedbackResponseDTO response = studentService.getFeedback(examId, studentId);
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            return handleStudentException(e);
        }
    }

    /*---------------------------------------------------------- 
							RAISE GRIEVANCES
	------------------------------------------------------------*/ 

    /**
     * Student raises a grievance for a specific sub-question result.
     *
     * Business rules enforced in service:
     * - Result must belong to requesting student
     * - One grievance per result maximum
     * - Must be within grievance deadline
     * - Evaluation must be complete
     *
     * @param request    GrievanceRequestDTO with resultId, reason, requestedMarks
     * @param authHeader Bearer token from Authorization header
     * @return 201 CREATED with grievance details
     *         400 BAD REQUEST if deadline passed or grievance already exists
     *         403 FORBIDDEN if result doesn't belong to this student
     *         404 NOT FOUND if result not found
     * @throws BadRequestException 
     */
    @PostMapping("/grievances")
    public ResponseEntity<?> raiseGrievance(
            @Valid @RequestBody GrievanceRequestDTO request,
            @RequestHeader("Authorization") String authHeader
    ) throws BadRequestException {
        try {
            String studentId = jwtUtils.extractUserId(authHeader.substring(7));

            GrievanceEntity saved = studentService.raiseGrievance(request, studentId);

            // Build clean response — no nested entity exposure
            StudentGrievanceResponseDTO response = new StudentGrievanceResponseDTO();
            response.setGrievanceId(saved.getId());
            response.setSubQuestionLabel(
                    saved.getResult().getSubQuestion().getSubQuestionLabel()
            );
            response.setGrievanceType(saved.getGrievanceType());
            response.setStatus(saved.getStatus());
            response.setStudentReason(saved.getStudentReason());
            response.setRequestedMarks(saved.getRequestedMarks());
            response.setAwardedMarks(saved.getAwardedMarks());
            response.setAiMarks(saved.getResult().getAiMarks());
            response.setFinalMarks(saved.getResult().getFinalMarks());
            response.setReviewerComment(saved.getReviewerComment());
            response.setRaisedAt(saved.getRaisedAt());
            response.setResolvedAt(saved.getResolvedAt());

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (RuntimeException e) {
            return handleStudentException(e);
        }
    }

    /*---------------------------------------------------------- 
						TRACK GRIEVANCES
	------------------------------------------------------------*/

    /**
     * Returns all grievances raised by the logged-in student.
     * Student can only see their own grievances — enforced at service level.
     *
     * @param authHeader Bearer token from Authorization header
     * @return 200 OK with list of all grievances and their current status
     *         PENDING → raised, not yet reviewed
     *         UNDER_REVIEW → faculty is reviewing
     *         RESOLVED → faculty awarded new marks
     *         REJECTED → faculty kept original marks
     * @throws BadRequestException 
     */
    @GetMapping("/grievances")
    public ResponseEntity<?> getMyGrievances(
            @RequestHeader("Authorization") String authHeader
    ) throws BadRequestException {
        try {
            String studentId = jwtUtils.extractUserId(authHeader.substring(7));

            List<StudentGrievanceResponseDTO> response =
                    studentService.getMyGrievances(studentId);

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            return handleStudentException(e);
        }
    }

    /*---------------------------------------------------------- 
						PRIVATE HELPER
	------------------------------------------------------------*/

    /**
     * Centralized exception handler for all student endpoints.
     * Maps service-layer RuntimeException messages to correct HTTP status codes.
     * Keeps all controller methods clean and consistent.
     *
     * @param e RuntimeException thrown by StudentService
     * @return appropriate ResponseEntity with status and message
     */
    private ResponseEntity<?> handleStudentException(RuntimeException e) {
        return switch (e.getMessage()) {
            case "EXAM_NOT_FOUND" ->
                ResponseEntity.status(HttpStatus.NOT_FOUND).body("Exam not found");
            case "STUDENT_NOT_FOUND" ->
                ResponseEntity.status(HttpStatus.NOT_FOUND).body("Student not found");
            case "ANSWER_SHEET_NOT_FOUND" ->
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("No answer sheet found for this exam");
            case "RESULT_NOT_FOUND" ->
                ResponseEntity.status(HttpStatus.NOT_FOUND).body("Result not found");
            case "FEEDBACK_NOT_AVAILABLE" ->
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Feedback not available yet");
            case "RESULTS_NOT_READY" ->
                ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("Results are not available yet — evaluation still in progress");
            case "UNAUTHORIZED_GRIEVANCE" ->
                ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("You can only raise grievances for your own results");
            case "GRIEVANCE_DEADLINE_PASSED" ->
                ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Grievance deadline has passed for this exam");
            case "GRIEVANCE_ALREADY_EXISTS" ->
                ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("You have already raised a grievance for this sub-question");
                
            case "ANSWERSHEET_NOT_READY" -> 
            		ResponseEntity.status(HttpStatus.FORBIDDEN).body("Answersheet are not available yet — evaluation still in progress");
            default ->
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Something went wrong: " + e.getMessage());
        };
    }
}