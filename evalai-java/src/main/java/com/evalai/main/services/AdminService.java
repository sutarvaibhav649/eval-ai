package com.evalai.main.services;

import com.evalai.main.dtos.request.ExamRequestDTO;
import com.evalai.main.dtos.request.SubjectRequestDTO;
import com.evalai.main.dtos.response.ExamResponseDTO;
import com.evalai.main.entities.*;
import com.evalai.main.enums.*;
import com.evalai.main.utils.BadRequestException;
import com.evalai.main.repositories.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AdminService {

    private final ExamRepository examRepository;
    private final AnswersheetRepository answersheetRepository;
    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;
    private final TaskLogsRepository taskLogsRepository;
    private final PythonService pythonService;

    @Value("${app.upload.base-path}")
    private String uploadBasePath;

    public AdminService(
            ExamRepository examRepository,
            AnswersheetRepository answersheetRepository,
            UserRepository userRepository,
            SubjectRepository subjectRepository,
            TaskLogsRepository taskLogsRepository,
            PythonService pythonService
    ) {
        this.examRepository = examRepository;
        this.subjectRepository = subjectRepository;
        this.userRepository = userRepository;
        this.answersheetRepository = answersheetRepository;
        this.taskLogsRepository = taskLogsRepository;
        this.pythonService = pythonService;
    }

    /*------------------------------------------------
                   SUBJECT
    -------------------------------------------------*/
    /**
     * Creates a new subject.
     * Subject code must be unique system-wide.
     */
    public SubjectEntity createSubject(SubjectRequestDTO requestDTO, String adminId)
            throws BadRequestException {

        if (subjectRepository.existsByCode(requestDTO.getCode())) {
            throw new BadRequestException("SUBJECT_CODE_EXISTS");
        }

        SubjectEntity subjectEntity = new SubjectEntity();
        subjectEntity.setName(requestDTO.getName());
        subjectEntity.setCode(requestDTO.getCode().toUpperCase());
        subjectEntity.setDepartment(requestDTO.getDepartment());
        subjectEntity.setSemester(requestDTO.getSemester());

        return subjectRepository.save(subjectEntity);
    }

    /*------------------------------------------------
                      EXAM
    -------------------------------------------------*/
    /**
     * Creates a new exam linked to one or more subjects.
     *
     * FIX 1: Now fetches a List<SubjectEntity> instead of a single subject.
     * FIX 2: Duplicate check now uses title + createdBy (not title + subjectId).
     *
     * @param requestDTO validated exam details — now has List<String> subjectIds
     * @param adminId    ID of the admin creating the exam (from JWT)
     * @return saved ExamEntity
     */
    public ExamEntity createExam(ExamRequestDTO requestDTO, String adminId)
            throws BadRequestException {

        // Step 1 — Verify admin exists
        UserEntity admin = userRepository.findById(adminId)
                .orElseThrow(() -> new BadRequestException("ADMIN_NOT_FOUND"));

        // Step 2 — Fetch ALL subjects from the provided IDs
        List<SubjectEntity> subjects = new ArrayList<>();
        for (String subjectId : requestDTO.getSubjectIds()) {
            SubjectEntity subject = subjectRepository.findById(subjectId)
                    .orElseThrow(() -> new BadRequestException(
                            "SUBJECT_NOT_FOUND: " + subjectId
                    ));
            subjects.add(subject);
        }

        // Step 3 — Check for duplicate exam title created by same admin
        if (examRepository.existsByTitleAndCreatedBy(requestDTO.getTitle(), admin)) {
            throw new RuntimeException("EXAM_ALREADY_EXISTS");
        }

        // Step 4 — Build and save exam with all subjects
        ExamEntity exam = new ExamEntity();
        exam.setTitle(requestDTO.getTitle());
        exam.setSubjects(subjects);         // FIX: setSubjects (List) not setSubject
        exam.setAcademicYear(requestDTO.getAcademicYear());
        exam.setCreatedBy(admin);
        exam.setDuration(requestDTO.getDuration());
        exam.setExamDate(requestDTO.getExamDate());
        exam.setGrievanceDeadline(requestDTO.getGrievanceDeadline());
        exam.setTotalMarks(requestDTO.getTotalMarks());
        exam.setStatus(ExamStatus.SCHEDULED);

        return examRepository.save(exam);
    }

    /*------------------------------------------------
                   BATCH UPLOAD
    -------------------------------------------------*/
    /**
     * Processes a batch of student answer sheet PDFs.
     * No changes to this method — not affected by exam-subject refactor.
     */
    public List<AnswersheetEntity> uploadAnswerSheets(
            String examId,
            String subjectId,
            List<MultipartFile> files,
            String adminId
    ) throws Exception {

        ExamEntity exam = examRepository.findById(examId)
                .orElseThrow(() -> new BadRequestException("EXAM_NOT_FOUND"));

        UserEntity admin = userRepository.findById(adminId)
                .orElseThrow(() -> new BadRequestException("ADMIN_NOT_EXISTS"));

        SubjectEntity subject = subjectRepository.findById(subjectId)  // ← ADD THIS
                .orElseThrow(() -> new BadRequestException("SUBJECT_NOT_FOUND"));

        // verify subject belongs to this exam
        if (!exam.getSubjects().contains(subject)) {              // ← ADD THIS
            throw new BadRequestException("SUBJECT_NOT_IN_EXAM");
        }

        // FIX: Validate file types before processing any files
        for (MultipartFile file : files) {
            String contentType = file.getContentType();
            if (contentType == null || !contentType.equals("application/pdf")) {
                throw new BadRequestException("ONLY_PDF_ALLOWED");
            }
        }

        List<AnswersheetEntity> savedAnswersheets = new ArrayList<>();

        for (MultipartFile file : files) {
            String studentId = null;

            try {
                String scanPdfDir = buildPath(uploadBasePath, "uploaded_pdf", examId, "qr_scan");
                String scanRawImagesDir = buildPath(uploadBasePath, "raw_images", examId, "qr_scan");

                createDirectories(scanPdfDir);
                createDirectories(scanRawImagesDir);

                String scanPdfPath = scanPdfDir + File.separator + UUID.randomUUID() + ".pdf";
                Files.write(Paths.get(scanPdfPath), file.getBytes());

                int pageCount = splitPdfToImages(scanPdfPath, scanRawImagesDir);
                List<String> scanImagePaths = new ArrayList<>();
                for (int page = 1; page <= pageCount; page++) {
                    scanImagePaths.add(scanRawImagesDir + File.separator + "page_" + page + ".png");
                }

                studentId = pythonService.extractStudentIdFromQr(scanImagePaths);
                UserEntity student = userRepository.findById(studentId)
                        .orElseThrow(() -> new BadRequestException("STUDENT_NOT_FOUND: " + studentId));

                String pdfDir = buildPath(uploadBasePath, "uploaded_pdf", examId, studentId);
                String rawImagesDir = buildPath(uploadBasePath, "raw_images", examId, studentId);

                createDirectories(pdfDir);
                createDirectories(rawImagesDir);

                String pdfPath = pdfDir + File.separator + "answer_sheet.pdf";
                Files.write(Paths.get(pdfPath), file.getBytes());
                splitPdfToImages(pdfPath, rawImagesDir);

                AnswersheetEntity answersheet = new AnswersheetEntity();
                answersheet.setExam(exam);
                answersheet.setSubject(subject);
                answersheet.setFilePath(pdfPath);
                answersheet.setFileType("PDF");
                answersheet.setStudent(student);
                answersheet.setUploadedBy(admin);
                answersheet.setOcrStatus(OcrStatus.PENDING);
                answersheet.setEvaluationStatus(EvaluationStatus.PENDING);

                AnswersheetEntity savedAnswersheet = answersheetRepository.save(answersheet);

                TaskLogsEntity taskLog = new TaskLogsEntity();
                taskLog.setTaskId(UUID.randomUUID().toString());
                taskLog.setAnswersheet(savedAnswersheet);
                taskLog.setStatus(TaskLogStatus.QUEUED);
                taskLogsRepository.save(taskLog);

                savedAnswersheets.add(savedAnswersheet);

            } catch (Exception e) {
                throw new RuntimeException("UPLOAD_FAILED_FOR_STUDENT_" + (studentId != null ? studentId : "UNKNOWN_QR")
                        + ": " + e.getMessage());
            }
        }

        return savedAnswersheets;
    }

    /*-----------------------------------------------------------
                       EVALUATION STATUS
    ------------------------------------------------------------*/
    public List<AnswersheetEntity> getEvaluationStatus(String examId)
            throws BadRequestException {
        ExamEntity exam = examRepository.findById(examId)
                .orElseThrow(() -> new BadRequestException("EXAM_NOT_FOUND"));
        return answersheetRepository.findByExam(exam);
    }

    /*---------------------------------------------------
     *           PRIVATE HELPER METHODS
     ---------------------------------------------------*/
    private String buildPath(String... parts) {
        return String.join(File.separator, parts);
    }

    private void createDirectories(String path) throws IOException {
        Files.createDirectories(Paths.get(path));
    }

    /*========================================================================
                                GET ALL EXAMS
    =========================================================================*/
    public List<ExamEntity> getAllExams(){
        List<ExamEntity> exams = examRepository.findAll();

        return exams;
    }

    private int splitPdfToImages(String pdfPath, String outputDir) throws IOException {
        try (PDDocument document = Loader.loadPDF(new File(pdfPath))) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();

            for (int page = 0; page < pageCount; page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, 150);
                String imagePath = outputDir + File.separator + "page_" + (page + 1) + ".png";
                ImageIO.write(image, "PNG", new File(imagePath));
            }

            return pageCount;
        }
    }
}
