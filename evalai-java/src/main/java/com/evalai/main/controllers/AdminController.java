package com.evalai.main.controllers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.evalai.main.dtos.request.ExamRequestDTO;
import com.evalai.main.dtos.request.SubjectRequestDTO;
import com.evalai.main.dtos.response.ExamResponseDTO;
import com.evalai.main.dtos.response.SubjectResponseDTO;
import com.evalai.main.entities.AnswersheetEntity;
import com.evalai.main.entities.ExamEntity;
import com.evalai.main.entities.SubjectEntity;
import com.evalai.main.enums.EvaluationStatus;
import com.evalai.main.services.AdminService;
import com.evalai.main.utils.JwtUtils;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Handles all Admin-facing HTTP endpoints.
 * All endpoints require ADMIN role — enforced via Spring Security.
 *
 * Endpoints:
 * POST /admin/subjects              → Create subject
 * POST /admin/exams                 → Create exam
 * POST /admin/answer-sheets/upload  → Batch upload student PDFs
 * GET  /admin/evaluation/status     → Check evaluation progress
 *
 * @author Vaibhav Sutar
 * @version 1.0
 */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController{
    private final AdminService adminService;
    private final JwtUtils jwtUtils;

    
    
    /*-------------------------------------------------
     * 					SUBJECT
     -------------------------------------------------*/
    
    /**
     * Creates a new subject.
     *
     * @param request   validated subject details
     * @param authHeader Bearer token from Authorization header
     * @return 201 CREATED with subject details
     *         409 CONFLICT if subject code already exists
     */
    @PostMapping("/subjects")
    public ResponseEntity<?> createSubject(
    		@Valid @RequestBody SubjectRequestDTO requestDTO,
    		@RequestHeader("Authorization") String authHeader
    	){
    		try {
			// extract admin id from access token
    			String adminId = jwtUtils.extractUserId(authHeader.substring(7));
    			
    			SubjectEntity saved = adminService.createSubject(requestDTO, adminId);
    			
    			SubjectResponseDTO responseDTO = new SubjectResponseDTO();
    			responseDTO.setSubjectId(saved.getId());
    			responseDTO.setName(saved.getName());
    			responseDTO.setCode(saved.getCode());
    			responseDTO.setCreatedAt(saved.getCreatedAt());
    			responseDTO.setName(saved.getDepartment());
    			responseDTO.setSemester(saved.getSemester());
    			
    			return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
		} catch (RuntimeException e) {
			if ("SUBJECT_CODE_EXISTS".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Subject with this code already exists");
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to create subject: " + e.getMessage());
		}
    }
    
    /*--------------------------------------------------------------
     					EXAM
     -------------------------------------------------------------*/
    
    /**
     * Creates a new exam linked to an existing subject.
     *
     * @param request   validated exam details including subjectId
     * @param authHeader Bearer token from Authorization header
     * @return 201 CREATED with exam details
     *         404 NOT FOUND if subject doesn't exist
     *         409 CONFLICT if exam title already exists for this subject
     */
    @PostMapping("/exam")
    public ResponseEntity<?> createExam(
    		@Valid @RequestBody ExamRequestDTO requestDTO,
    		@RequestHeader("Authorization") String authHeader
    	){
    		try {
    			String adminId = jwtUtils.extractUserId(authHeader.substring(7));
    			
    			ExamEntity saved = adminService.createExam(requestDTO, adminId);
    			
    			// Build response
                ExamResponseDTO response = new ExamResponseDTO();
                response.setId(saved.getId());
                response.setTitle(saved.getTitle());
                response.setSubjectName(saved.getSubject().getName());
                response.setSubjectCode(saved.getSubject().getCode());
                response.setAcademicYear(saved.getAcademicYear());
                response.setExamDate(saved.getExamDate());
                response.setTotalMarks(saved.getTotalMarks());
                response.setDurationMinutes(saved.getDuration());
                response.setStatus(saved.getStatus());
                response.setGrievanceDeadline(saved.getGrievanceDeadline());
                response.setCreatedAt(saved.getCreatedAt());

                return ResponseEntity.status(HttpStatus.CREATED).body(response);
    			
		} catch (Exception e) {
			if ("SUBJECT_NOT_FOUND".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Subject not found");
            }
            if ("EXAM_ALREADY_EXISTS".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("An exam with this title already exists for this subject");
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to create exam: " + e.getMessage());
		}
    }   
    
    /*-------------------------------------------------------
     * 					Batch Upload
     -------------------------------------------------------*/
    
    /**
     * Accepts a batch of student answer sheet PDFs for a given exam.
     *
     * Request is multipart/form-data with:
     * - examId          (String)
     * - studentIds      (List<String>) — must match order of files
     * - files           (List<MultipartFile>) — one PDF per student
     *
     * @return 201 CREATED with count of sheets queued
     *         404 NOT FOUND if exam or any student not found
     *         400 BAD REQUEST if file count doesn't match student count
     */
    @PostMapping(value = "/answer-sheets/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadAnswerSheets(
            @RequestParam("examId") String examId,
            @RequestParam("studentIds") List<String> studentIds,
            @RequestParam("files") List<MultipartFile> files,
            @RequestHeader("Authorization") String authHeader
    ) {
        try {
            String adminId = jwtUtils.extractUserId(authHeader.substring(7));

            List<AnswersheetEntity> saved = adminService.uploadAnswerSheets(
                    examId, studentIds, files, adminId
            );

            // Build simple summary response
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Batch upload successful");
            response.put("examId", examId);
            response.put("totalUploaded", saved.size());
            response.put("status", "QUEUED");

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (RuntimeException e) {
            if (e.getMessage().startsWith("STUDENT_NOT_FOUND")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(e.getMessage());
            }
            if ("EXAM_NOT_FOUND".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Exam not found");
            }
            if ("FILE_STUDENT_COUNT_MISMATCH".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Number of files must match number of student IDs");
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Upload failed: " + e.getMessage());
        } catch (Exception e) {
			// TODO Auto-generated catch block
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
		}
    }
    
    /*---------------------------------------------------------
     					EVALUATION STATUS
    ----------------------------------------------------------*/ 

    /**
     * Returns evaluation progress for all students in an exam.
     * Used by Admin dashboard to show real-time processing status.
     *
     * @param examId ID of the exam to check
     * @return 200 OK with list of student statuses
     */
    @GetMapping("/evaluation/status")
    public ResponseEntity<?> getEvaluationStatus(
            @RequestParam("examId") String examId
    ) {
        try {
            List<AnswersheetEntity> sheets = adminService.getEvaluationStatus(examId);

            // Build status summary per student
            List<Map<String, Object>> statusList = sheets.stream().map(sheet -> {
                Map<String, Object> entry = new HashMap<>();
                entry.put("answersheetId", sheet.getId());
                entry.put("studentId", sheet.getStudent().getId());
                entry.put("studentName", sheet.getStudent().getName());
                entry.put("ocrStatus", sheet.getOcrStatus());
                entry.put("evaluationStatus", sheet.getEvaluationStatus());
                entry.put("totalMarks", sheet.getMarks());
                return entry;
            }).toList();

            // Overall summary counts
            Map<String, Object> response = new HashMap<>();
            response.put("examId", examId);
            response.put("totalStudents", sheets.size());
            response.put("completed", sheets.stream()
                    .filter(s -> s.getEvaluationStatus() == EvaluationStatus.COMPLETED).count());
            response.put("pending", sheets.stream()
                    .filter(s -> s.getEvaluationStatus() == EvaluationStatus.PENDING).count());
            response.put("failed", sheets.stream()
                    .filter(s -> s.getEvaluationStatus() == EvaluationStatus.FAILED).count());
            response.put("students", statusList);

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            if ("EXAM_NOT_FOUND".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Exam not found");
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to fetch status: " + e.getMessage());
        }
    }
    
}