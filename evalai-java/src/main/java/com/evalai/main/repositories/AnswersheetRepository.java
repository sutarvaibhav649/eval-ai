package com.evalai.main.repositories;

import java.util.List;
import java.util.Optional;

import com.evalai.main.entities.SubjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import com.evalai.main.entities.AnswersheetEntity;
import com.evalai.main.entities.ExamEntity;
import com.evalai.main.entities.UserEntity;
import com.evalai.main.enums.EvaluationStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnswersheetRepository extends JpaRepository<AnswersheetEntity, String> {

	List<AnswersheetEntity> findByExam(ExamEntity exam);

    // Fix — remove this broken method, it's a duplicate of the one below
    // List<AnswersheetEntity> findByEvaluationStatus(ExamEntity exam, EvaluationStatus status);

    Optional<AnswersheetEntity> findByExamAndStudent(ExamEntity exam, UserEntity student);

    List<AnswersheetEntity> findByExamAndEvaluationStatus(ExamEntity exam, EvaluationStatus status);

    @Query("SELECT a FROM AnswersheetEntity a " +
            "JOIN FETCH a.exam e " +
            "JOIN FETCH e.subjects " +
            "WHERE a.id = :id")
    Optional<AnswersheetEntity> findByIdWithExamAndSubjects(@Param("id") String id);

    List<AnswersheetEntity> findByExamAndSubjectAndEvaluationStatus(ExamEntity exam, SubjectEntity subject, EvaluationStatus evaluationStatus);
}
