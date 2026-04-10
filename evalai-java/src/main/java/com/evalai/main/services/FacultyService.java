package com.evalai.main.services;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.evalai.main.dtos.response.EmbeddingResponseDTO;
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

import org.springframework.transaction.annotation.Transactional;
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
    public QuestionPaperEntity createQuestionPaper(
            QuestionPaperRequestDTO requestDTO,
            String facultyId,
            org.springframework.web.multipart.MultipartFile file
    ) throws BadRequestException {

        ExamEntity exam = examRepository.findById(requestDTO.getExamId())
                .orElseThrow(() -> new BadRequestException("EXAM_NOT_FOUND"));

        UserEntity faculty = userRepository.findById(facultyId)
                .orElseThrow(() -> new BadRequestException("FACULTY_NOT_FOUND"));

        if (questionPaperRepository.existsByExamAndSetLable(exam, requestDTO.getSetLable())) {
            throw new BadRequestException("SET_LABEL_EXISTS");
        }

        try {
            String paperDir = uploadBasePath + File.separator + "question_papers"
                    + File.separator + requestDTO.getExamId();
            Files.createDirectories(Paths.get(paperDir));
            String filePath = paperDir + File.separator
                    + requestDTO.getSetLable().replace(" ", "-") + ".pdf";
            Files.write(Paths.get(filePath), file.getBytes());

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
    public QuestionEntity createQuestion(QuestionRequestDTO requestDTO)
            throws BadRequestException {

        QuestionPaperEntity questionPaper = questionPaperRepository
                .findById(requestDTO.getQuestionPaperId())
                .orElseThrow(() -> new BadRequestException("QUESTION_PAPER_NOT_FOUND"));

        if (questionRepository.existsByQuestionNumberAndQuestionPaper(
                requestDTO.getQuestionNumber(), questionPaper)) {
            throw new BadRequestException("QUESTION_NUMBER_ALREADY_EXISTS");
        }

        QuestionEntity question = new QuestionEntity();
        question.setQuestionNumber(requestDTO.getQuestionNumber());
        question.setQuestionPaper(questionPaper);
        question.setTotalMarks(requestDTO.getTotalMarks());
        return questionRepository.save(question);
    }

    /*-----------------------------------------------------
                   Sub-Question
    ------------------------------------------------------*/
    public SubQuestionEntity createSubQuestion(SubQuestionRequestDTO requestDTO)
            throws BadRequestException {

        QuestionEntity question = questionRepository.findById(requestDTO.getQuestionId())
                .orElseThrow(() -> new BadRequestException("QUESTION_NOT_FOUND"));

        if (subQuestionRepository.existsBySubQuestionLabelAndQuestion(
                requestDTO.getSubQuestionLabel(), question)) {
            throw new BadRequestException("LABLE_ALREADY_EXISTS");
        }

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
     * FIX: generateEmbeddings() now uses a typed DTO (EmbeddingResponseDTO)
     * instead of raw Map — eliminates unsafe ClassCastException risk.
     */
    public ModelAnswerEntity createModelAnswer(
            ModelAnswerRequestDTO requestDTO,
            String facultyId
    ) throws BadRequestException {

        SubQuestionEntity subQuestion = subQuestionRepository
                .findById(requestDTO.getSubQuestionId())
                .orElseThrow(() -> new BadRequestException("SUBQUESTION_NOT_FOUND"));

        UserEntity faculty = userRepository.findById(facultyId)
                .orElseThrow(() -> new BadRequestException("FACULTY_NOT_FOUND"));

        if (modelAnswerRepository.existsBySubQuestion(subQuestion)) {
            throw new BadRequestException("MODEL_ANSWER_EXISTS");
        }

        ModelAnswerEntity modelAnswer = new ModelAnswerEntity();
        modelAnswer.setAnswerText(requestDTO.getAnswerText());
        modelAnswer.setSubQuestion(subQuestion);
        modelAnswer.setKeyConcepts(requestDTO.getKeyConcepts());
        modelAnswer.setUser(faculty);

        ModelAnswerEntity savedModelAnswer = modelAnswerRepository.save(modelAnswer);

        try {
            float[] embeddings = generateEmbeddings(requestDTO.getAnswerText());
            savedModelAnswer.setEmbedding(embeddings);
            savedModelAnswer = modelAnswerRepository.save(savedModelAnswer);
        } catch (Exception e) {
            System.err.println("WARNING: Embedding generation failed for sub-question "
                    + requestDTO.getSubQuestionId() + ": " + e.getMessage());
        }

        return savedModelAnswer;
    }

    /*----------------------------------------------------------------
                      Grievance review
    -----------------------------------------------------------------*/
    public List<GrievanceEntity> allPendingGrievance(String facultyId)
            throws BadRequestException {

        userRepository.findById(facultyId)
                .orElseThrow(() -> new BadRequestException("FACULTY_NOT_FOUND"));

        return grievanceRepository.findByStatus(GrievanceStatus.PENDING);
    }

    @Transactional
    public GrievanceEntity grievanceReview(
            String grievanceId,
            GrievanceReviewRequest request,
            String facultyId
    ) throws BadRequestException {

        GrievanceEntity grievance = grievanceRepository.findById(grievanceId)
                .orElseThrow(() -> new BadRequestException("GRIEVANCE_NOT_FOUND"));

        UserEntity faculty = userRepository.findById(facultyId)
                .orElseThrow(() -> new BadRequestException("FACULTY_NOT_EXISTS"));

        if (grievance.getStatus() == GrievanceStatus.RESOLVED
                || grievance.getStatus() == GrievanceStatus.REJECTED) {
            throw new RuntimeException("GRIEVANCE_ALREADY_CLOSED");
        }

        ResultEntity result = grievance.getResult();
        if (request.getStatus() == GrievanceStatus.RESOLVED) {
            result.setFinalMarks(request.getAwardedMarks());
            result.setIsOverriden(true);
            result.setOverridenBy(faculty);
            result.setOverrideReason(request.getReviewerComment());
            resultRepository.save(result);
        }

        grievance.setAwardedMarks(request.getAwardedMarks());
        grievance.setReviewerComment(request.getReviewerComment());
        grievance.setStatus(request.getStatus());
        grievance.setReviewedBy(faculty);
        grievance.setResolvedAt(LocalDateTime.now());

        return grievanceRepository.save(grievance);
    }

    /*---------------------------------------------------------------------
                      Helper — Embedding generation
     -------------------------------------------------------------------*/
    /**
     * FIX: was using raw Map — unsafe ClassCastException risk.
     * Now uses typed EmbeddingResponseDTO for safe deserialization.
     *
     * If Python returns a shape we don't expect, it fails fast with
     * a clear error instead of a cryptic ClassCastException at runtime.
     */
    private float[] generateEmbeddings(String text) {
        String url = pythonBaseUrl + "/embeddings/generate";

        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("text", text);

        // FIX: Use typed DTO instead of raw Map
        ResponseEntity<EmbeddingResponseDTO> response = restTemplate.postForEntity(
                url, requestBody, EmbeddingResponseDTO.class
        );

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            List<Double> embeddingList = response.getBody().getEmbedding();

            if (embeddingList == null || embeddingList.isEmpty()) {
                throw new RuntimeException("EMPTY_EMBEDDING_RETURNED");
            }

            float[] embedding = new float[embeddingList.size()];
            for (int i = 0; i < embeddingList.size(); i++) {
                embedding[i] = embeddingList.get(i).floatValue();
            }
            return embedding;
        }

        throw new RuntimeException("EMBEDDING_GENERATION_FAILED");
    }
}