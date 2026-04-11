package com.evalai.main.repositories;

import java.util.List;
import java.util.Optional;

import com.evalai.main.entities.SubjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.evalai.main.entities.ExamEntity;
import com.evalai.main.entities.QuestionPaperEntity;

public interface QuestionPaperRepository extends JpaRepository<QuestionPaperEntity, String> {

    List<QuestionPaperEntity> findByExamAndSubject(ExamEntity exam, SubjectEntity subject);
    boolean existsByExamAndSetLable(ExamEntity exam, String setLable);
    
    @Query("SELECT qp FROM QuestionPaperEntity qp LEFT JOIN FETCH qp.questions WHERE qp.id = :id")
    Optional<QuestionPaperEntity> findByIdWithQuestions(@Param("id") String id);

    @Query("SELECT qp FROM QuestionPaperEntity qp LEFT JOIN FETCH qp.questions q LEFT JOIN FETCH q.subQuestions WHERE qp.id = :id")
    Optional<QuestionPaperEntity> findByIdWithQuestionsAndSubQuestions(@Param("id") String id);
}
