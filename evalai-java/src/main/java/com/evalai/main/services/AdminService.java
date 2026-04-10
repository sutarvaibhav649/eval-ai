package com.evalai.main.services;

import com.evalai.main.dtos.request.ExamRequestDTO;
import com.evalai.main.dtos.request.SubjectRequestDTO;
import com.evalai.main.entities.*;
import com.evalai.main.enums.*;
import com.evalai.main.utils.BadRequestException;
import com.evalai.main.repositories.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Value("${app.upload.base-path}")
    private String uploadBasePath;

    public AdminService(
            ExamRepository examRepository,
            AnswersheetRepository answersheetRepository,
            UserRepository userRepository,
            SubjectRepository subjectRepository,
            TaskLogsRepository taskLogsRepository
    ) {
        this.examRepository = examRepository;
        this.subjectRepository = subjectRepository;
        this.userRepository = userRepository;
        this.answersheetRepository = answersheetRepository;
        this.taskLogsRepository = taskLogsRepository;
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
            List<String> studentIds,
            List<MultipartFile> files,
            String adminId
    ) throws Exception {

        ExamEntity exam = examRepository.findById(examId)
                .orElseThrow(() -> new BadRequestException("EXAM_NOT_FOUND"));

        UserEntity admin = userRepository.findById(adminId)
                .orElseThrow(() -> new BadRequestException("ADMIN_NOT_EXISTS"));

        if (files.size() != studentIds.size()) {
            throw new BadRequestException("FILE_STUDENT_COUNT_MISMATCH");
        }

        // FIX: Validate file types before processing any files
        for (MultipartFile file : files) {
            String contentType = file.getContentType();
            if (contentType == null || !contentType.equals("application/pdf")) {
                throw new BadRequestException("ONLY_PDF_ALLOWED");
            }
        }

        List<AnswersheetEntity> savedAnswersheets = new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String studentId = studentIds.get(i);

            UserEntity student = userRepository.findById(studentId)
                    .orElseThrow(() -> new BadRequestException(
                            "STUDENT_NOT_FOUND: " + studentId
                    ));

            try {
                String pdfDir = buildPath(uploadBasePath, "uploaded_pdf", examId, studentId);
                String rawImagesDir = buildPath(uploadBasePath, "raw_images", examId, studentId);

                createDirectories(pdfDir);
                createDirectories(rawImagesDir);

                String pdfPath = pdfDir + File.separator + "answer_sheet.pdf";
                Files.write(Paths.get(pdfPath), file.getBytes());

                int pageCount = splitPdfToImages(pdfPath, rawImagesDir);

                AnswersheetEntity answersheet = new AnswersheetEntity();
                answersheet.setExam(exam);
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
                throw new RuntimeException("UPLOAD_FAILED_FOR_STUDENT_" + studentId
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