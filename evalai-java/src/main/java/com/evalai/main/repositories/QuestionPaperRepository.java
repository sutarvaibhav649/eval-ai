package com.evalai.main.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.evalai.main.entities.ExamEntity;
import com.evalai.main.entities.QuestionPaperEntity;

public interface QuestionPaperRepository extends JpaRepository<QuestionPaperEntity, String> {

    List<QuestionPaperEntity> findByExam(ExamEntity exam);
    boolean existsByExamAndSetLable(ExamEntity exam, String setLable);
}
