package com.evalai.main.services;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import javax.imageio.ImageIO;

import org.apache.coyote.BadRequestException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.evalai.main.dtos.request.ExamRequestDTO;
import com.evalai.main.dtos.request.SubjectRequestDTO;
import com.evalai.main.entities.ExamEntity;
import com.evalai.main.entities.SubjectEntity;
import com.evalai.main.entities.UserEntity;
import com.evalai.main.enums.EvaluationStatus;
import com.evalai.main.enums.ExamStatus;
import com.evalai.main.enums.OcrStatus;
import com.evalai.main.enums.TaskLogStatus;
import com.evalai.main.repositories.AnswersheetRepository;
import com.evalai.main.repositories.ExamRepository;
import com.evalai.main.repositories.SubjectRepository;
import com.evalai.main.repositories.TaskLogsRepository;
import com.evalai.main.repositories.UserRepository;
import com.evalai.main.entities.*;

/**
 * Handles all Admin business logic: 1. Subject creation 2. Exam creation 3.
 * Batch PDF upload + directory structure creation 4. Evaluation trigger
 *
 * @author Vaibhav Sutar
 * @version 1.0
 */
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
     * Creates a new subject. Throws if subject code already exists — codes must
     * be unique across the system.
     *
     * @param request validated SubjectRequestDTO
     * @param adminId ID of the admin creating the subject (from JWT)
     * @return saved SubjectEntity
     * @throws BadRequestException 
     */
    public SubjectEntity createSubject(SubjectRequestDTO requestDTO, String admin) throws BadRequestException {

        // step-1: check if the subject code already exists
        if (subjectRepository.existsByCode(requestDTO.getCode())) {
            throw new BadRequestException("SUBJECT_CODE_EXISTS");
        }

        // step-2: set all the properties of Subject entity
        SubjectEntity subjectEntity = new SubjectEntity();
        subjectEntity.setName(requestDTO.getName());
        subjectEntity.setCode(requestDTO.getCode().toUpperCase());
        subjectEntity.setDepartment(requestDTO.getDepartment());
        subjectEntity.setSemester(requestDTO.getSemester());

        //step-3: return the Subject entity
        return subjectRepository.save(subjectEntity);
    }

    /*------------------------------------------------
							EXAM
	-------------------------------------------------*/
    /**
     * Creates a new exam linked to an existing subject. Throws if subject not
     * found or duplicate exam title exists for same subject.
     *
     * @param request validated ExamRequestDTO
     * @param adminId ID of the admin creating the exam (from JWT)
     * @return saved ExamEntity
     * @throws BadRequestException 
     */
    public ExamEntity createExam(ExamRequestDTO requestDTO, String adminId) throws BadRequestException {

        // step-1: Check if the subject already exists in the database
        SubjectEntity subject = subjectRepository.findById(requestDTO.getSubjectId())
                .orElseThrow(() -> new BadRequestException("SUBJECT_NOT_FOUND"));

        // step-2: verify that admin exists
        UserEntity admin = userRepository.findById(adminId)
                .orElseThrow(() -> new BadRequestException("ADMIN_NOT_FOUND"));

        // step-3: check is there any exam already exists with same details
        if (examRepository.existsByTitleAndSubject_Id(requestDTO.getTitle(), requestDTO.getSubjectId())) {
            throw new RuntimeException("EXAM_ALREADY_EXISTS");
        }

        // step-4: build and save exam
        ExamEntity exam = new ExamEntity();
        exam.setTitle(requestDTO.getTitle());
        exam.setSubject(subject);
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
     *
     * For each PDF: 1. Creates directory structure on disk 2. Saves PDF to
     * upload/uploaded_pdf/examId/studentId/ 3. Splits PDF into individual page
     * images → upload/raw_images/examId/studentId/ 4. Creates AnswerSheet
     * record in DB (status: PENDING) 5. Creates TaskLog record (status: QUEUED)
     *
     * @param examId ID of the exam these sheets belong to
     * @param studentIds list of student IDs matching order of files
     * @param files list of uploaded PDF files
     * @param adminId ID of the uploading admin (from JWT)
     * @return list of created AnswerSheetEntity records
     * @throws Exception
     */
    public List<AnswersheetEntity> uploadAnswerSheets(
            String examId,
            List<String> studentIds,
            List<MultipartFile> files,
            String adminId
    ) throws Exception {
        //step-1: validate exam exists
        ExamEntity exam = examRepository.findById(examId)
                .orElseThrow(() -> new BadRequestException("EXAM_NOT_FOUND"));

        // step-2: validate admin exists
        UserEntity admin = userRepository.findById(adminId)
                .orElseThrow(() -> new BadRequestException("ADMIN_NOT_EXISTS"));

        // step-3: validate file count matches student count
        if (files.size() != studentIds.size()) {
            throw new BadRequestException("FILES_AND_STUDENT_COUNT_MISMATCH");
        }

        List<AnswersheetEntity> savedAnswersheets = new ArrayList<>();

        // step-4: save the files 
        // iterate through all files
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String studentId = studentIds.get(i);

            // step-5: verify is student exists
            UserEntity student = userRepository.findById(studentId)
                    .orElseThrow(() -> new BadRequestException("STUDENT_NOT_EXITST_WITH_" + studentId));

            try {
                // step-6: Build directory
                String pdfDir = buildPath(uploadBasePath, "uploaded_pdf", examId, studentId);
                String rawImagesDir = buildPath(uploadBasePath, "raw_images", examId, studentId);

                // step-7: create directories
                createDirectories(pdfDir);
                createDirectories(rawImagesDir);

                //step-8: Save PDFs to disks
                String pdfPath = pdfDir + File.separator + "answer_sheet.pdf";
                Path pdfFilePath = Paths.get(pdfPath);
                Files.write(pdfFilePath, file.getBytes());

                // Step 9 — Split PDF into individual page images
                int pageCount = splitPdfToImages(pdfPath, rawImagesDir);

                // Step 10 — Create AnswerSheet record in DB
                AnswersheetEntity answersheet = new AnswersheetEntity();
                answersheet.setExam(exam);
                answersheet.setFilePath(pdfPath);
                answersheet.setFileType("PDF");
                answersheet.setStudent(student);
                answersheet.setUploadedBy(admin);
                answersheet.setOcrStatus(OcrStatus.PENDING);
                answersheet.setEvaluationStatus(EvaluationStatus.PENDING);

                AnswersheetEntity savedAnswersheet = answersheetRepository.save(answersheet);

                // Step 11 — Create TaskLog record
                TaskLogsEntity taskLog = new TaskLogsEntity();

                taskLog.setTaskId(UUID.randomUUID().toString());
                taskLog.setAnswersheet(savedAnswersheet);
                taskLog.setStatus(TaskLogStatus.QUEUED);
                taskLogsRepository.save(taskLog);

                savedAnswersheets.add(savedAnswersheet);

            } catch (Exception e) {
                throw new RuntimeException(e.getMessage());
            }
        }

        return savedAnswersheets;
    }

    /*-----------------------------------------------------------
    						EVALUATION STATUS
    ------------------------------------------------------------*/
    /**
     * Returns the current evaluation status of all answer sheets for an exam.
     * Used by Admin dashboard to show real-time progress.
     *
     * @param examId ID of the exam
     * @return list of AnswerSheetEntity with their current statuses
     * @throws BadRequestException 
     */
    public List<AnswersheetEntity> getEvaluationStatus(String examId) throws BadRequestException {
        ExamEntity exam = examRepository.findById(examId)
                .orElseThrow(() -> new BadRequestException("EXAM_NOT_FOUND"));
        return answersheetRepository.findByExam(exam);
    }

    /*---------------------------------------------------
     * 				PRIVATE HELPER METHODS
     ---------------------------------------------------*/
    /**
     * Builds a file system path from parts. Example: buildPath("upload",
     * "uploaded_pdf", "exam1", "student1") →
     * "upload/uploaded_pdf/exam1/student1"
     */
    private String buildPath(String... parts) {
        return String.join(File.separator, parts);
    }

    /**
     * Creates all directories in the given path if they don't exist.
     */
    private void createDirectories(String path) throws IOException {
        Files.createDirectories(Paths.get(path));
    }

    /**
     * Splits a PDF file into individual PNG images, one per page. Uses Apache
     * PDFBox.
     *
     * Writes files as: page_1.png, page_2.png, page_3.png ... into the provided
     * output directory.
     *
     * @param pdfPath full path to the PDF file
     * @param outputDir directory to write page images into
     * @return number of pages extracted
     */
    private int splitPdfToImages(String pdfPath, String outputDir) throws IOException {
        try (PDDocument document = Loader.loadPDF(new File(pdfPath))) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();

            for (int page = 0; page < pageCount; page++) {
                // Render at 300 DPI for good OCR quality
                BufferedImage image = renderer.renderImageWithDPI(page, 300);
                String imagePath = outputDir + File.separator + "page_" + (page + 1) + ".png";
                ImageIO.write(image, "PNG", new File(imagePath));
            }

            return pageCount;
        }
    }
}
