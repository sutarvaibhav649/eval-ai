package com.evalai.main.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.evalai.main.entities.AnswersheetEntity;
import com.evalai.main.entities.ExamEntity;
import com.evalai.main.entities.UserEntity;
import com.evalai.main.enums.EvaluationStatus;

public interface AnswersheetRepository extends JpaRepository<AnswersheetEntity, String> {

    List<AnswersheetEntity> findByExam(ExamEntity exam);

    List<AnswersheetEntity> findByEvaluationStatus(ExamEntity exam, EvaluationStatus status);
    
    Optional<AnswersheetEntity> findByExamAndStudent(ExamEntity exam, UserEntity student);
}
