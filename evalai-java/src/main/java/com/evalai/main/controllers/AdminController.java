package com.evalai.main.controllers;

import com.evalai.main.dtos.*;
import com.evalai.main.dtos.request.ExamRequestDTO;
import com.evalai.main.dtos.request.SubjectRequestDTO;
import com.evalai.main.dtos.response.ExamResponseDTO;
import com.evalai.main.dtos.response.StudentFeedbackResponseDTO;
import com.evalai.main.dtos.response.StudentResultResponseDTO;
import com.evalai.main.dtos.response.SubjectResponseDTO;
import com.evalai.main.entities.*;
import com.evalai.main.enums.EvaluationStatus;
import com.evalai.main.services.StudentService;
import com.evalai.main.utils.BadRequestException;
import com.evalai.main.services.AdminService;
import com.evalai.main.utils.JwtUtils;
import com.evalai.main.repositories.SubjectRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final StudentService studentService;
    private final JwtUtils jwtUtils;
    private final SubjectRepository subjectRepository;

    /*-------------------------------------------------
     *              SUBJECT
     -------------------------------------------------*/
    @PostMapping("/subjects")
    public ResponseEntity<?> createSubject(
            @Valid @RequestBody SubjectRequestDTO requestDTO,
            @RequestHeader("Authorization") String authHeader
    ) throws BadRequestException {
        try {
            String adminId = jwtUtils.extractUserId(authHeader.substring(7));
            SubjectEntity saved = adminService.createSubject(requestDTO, adminId);

            SubjectResponseDTO responseDTO = new SubjectResponseDTO();
            responseDTO.setSubjectId(saved.getId());
            responseDTO.setName(saved.getName());
            responseDTO.setCode(saved.getCode());
            responseDTO.setCreatedAt(saved.getCreatedAt());
            responseDTO.setDepartment(saved.getDepartment());
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

    /*-------------------------------------------------
     *         GET ALL SUBJECTS  ← NEW ENDPOINT
     -------------------------------------------------*/
    @GetMapping("/subjects")
    public ResponseEntity<List<SubjectResponseDTO>> getAllSubjects() {
        List<SubjectEntity> subjects = subjectRepository.findAll();

        List<SubjectResponseDTO> responseDTOs = subjects.stream().map(s -> {
            SubjectResponseDTO dto = new SubjectResponseDTO();
            dto.setSubjectId(s.getId());
            dto.setName(s.getName());
            dto.setCode(s.getCode());
            dto.setDepartment(s.getDepartment());
            dto.setSemester(s.getSemester());
            dto.setCreatedAt(s.getCreatedAt());
            return dto;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(responseDTOs);
    }

    /*--------------------------------------------------------------
     *                      EXAM
     -------------------------------------------------------------*/
    @PostMapping("/exam")
    public ResponseEntity<?> createExam(
            @Valid @RequestBody ExamRequestDTO requestDTO,
            @RequestHeader("Authorization") String authHeader
    ) {
        try {
            String adminId = jwtUtils.extractUserId(authHeader.substring(7));
            ExamEntity saved = adminService.createExam(requestDTO, adminId);

            List<ExamResponseDTO.SubjectInfo> subjectInfos = saved.getSubjects().stream()
                    .map(s -> {
                        ExamResponseDTO.SubjectInfo info = new ExamResponseDTO.SubjectInfo();
                        info.setSubjectId(s.getId());
                        info.setName(s.getName());
                        info.setCode(s.getCode());
                        info.setDepartment(s.getDepartment());
                        info.setSemester(s.getSemester());
                        return info;
                    })
                    .collect(Collectors.toList());

            ExamResponseDTO response = new ExamResponseDTO();
            response.setId(saved.getId());
            response.setTitle(saved.getTitle());
            response.setSubjects(subjectInfos);
            response.setAcademicYear(saved.getAcademicYear());
            response.setExamDate(saved.getExamDate());
            response.setTotalMarks(saved.getTotalMarks());
            response.setDurationMinutes(saved.getDuration());
            response.setStatus(saved.getStatus());
            response.setGrievanceDeadline(saved.getGrievanceDeadline());
            response.setCreatedAt(saved.getCreatedAt());

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().startsWith("SUBJECT_NOT_FOUND")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            if ("EXAM_ALREADY_EXISTS".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("An exam with this title already exists");
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to create exam: " + e.getMessage());
        }
    }

    /*-------------------------------------------------------
     *              Batch Upload
     -------------------------------------------------------*/
    @PostMapping(value = "/answer-sheets/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadAnswerSheets(
            @RequestParam("examId") String examId,
            @RequestParam("subjectId") String subjectId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestHeader("Authorization") String authHeader
    ) {
        try {
            String adminId = jwtUtils.extractUserId(authHeader.substring(7));

            List<AnswersheetEntity> saved = adminService.uploadAnswerSheets(
                    examId, subjectId, files, adminId
            );

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Batch upload successful");
            response.put("examId", examId);
            response.put("totalUploaded", saved.size());
            response.put("status", "QUEUED");

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("STUDENT_NOT_FOUND")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            if ("EXAM_NOT_FOUND".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Exam not found");
            }
            if ("ONLY_PDF_ALLOWED".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Only PDF files are allowed for answer sheet upload");
            }
            if (e.getMessage() != null && e.getMessage().contains("QR_SCAN_FAILED")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("QR scan failed. Ensure each uploaded PDF contains a readable student QR code.");
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Upload failed: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.getMessage());
        }
    }

    /*---------------------------------------------------------
     *                  EVALUATION STATUS
     ----------------------------------------------------------*/
    @GetMapping("/evaluation/status")
    public ResponseEntity<?> getEvaluationStatus(
            @RequestParam("examId") String examId
    ) throws BadRequestException {
        try {
            List<AnswersheetEntity> sheets = adminService.getEvaluationStatus(examId);

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
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Exam not found");
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to fetch status: " + e.getMessage());
        }
    }

    /*---------------------------------------------------------
     *                  GET ALL EXAMS
     ----------------------------------------------------------*/
    @GetMapping("/exam")
    public ResponseEntity<List<ExamResponseDTO>> getAllExams() {
        List<ExamEntity> exams = adminService.getAllExams();

        List<ExamResponseDTO> responseDTOS = new ArrayList<>();

        for (ExamEntity e : exams) {
            List<ExamResponseDTO.SubjectInfo> subjectInfos = new ArrayList<>();
            ExamResponseDTO responseDTO = new ExamResponseDTO();

            for (SubjectEntity s : e.getSubjects()) {
                ExamResponseDTO.SubjectInfo subjectInfo = new ExamResponseDTO.SubjectInfo();
                subjectInfo.setSubjectId(s.getId());
                subjectInfo.setCode(s.getCode());
                subjectInfo.setName(s.getName());
                subjectInfo.setDepartment(s.getDepartment());
                subjectInfo.setSemester(s.getSemester());
                subjectInfos.add(subjectInfo);
            }

            responseDTO.setId(e.getId());
            responseDTO.setTitle(e.getTitle());
            responseDTO.setSubjects(subjectInfos);
            responseDTO.setExamDate(e.getExamDate());
            responseDTO.setDurationMinutes(e.getDuration());
            responseDTO.setAcademicYear(e.getAcademicYear());
            responseDTO.setTotalMarks(e.getTotalMarks());
            responseDTO.setStatus(e.getStatus());
            responseDTO.setGrievanceDeadline(e.getGrievanceDeadline());
            responseDTO.setCreatedAt(e.getCreatedAt());
            responseDTOS.add(responseDTO);
        }

        return new ResponseEntity<>(responseDTOS, HttpStatus.OK);
    }

    /*---------------------------------------------------------
     *              STUDENT RESULT & FEEDBACK (ADMIN VIEW)
     ----------------------------------------------------------*/
    @GetMapping("/student/{studentId}/exam/{examId}/result")
    public ResponseEntity<StudentResultResponseDTO> getStudentResult(
            @PathVariable String studentId,
            @PathVariable String examId
    ) throws Exception {
        return ResponseEntity.ok(studentService.getResult(examId, studentId));
    }

    @GetMapping("/student/{studentId}/exam/{examId}/feedback")
    public ResponseEntity<StudentFeedbackResponseDTO> getStudentFeedback(
            @PathVariable String studentId,
            @PathVariable String examId
    ) throws Exception {
        return ResponseEntity.ok(studentService.getFeedback(examId, studentId));
    }
}
