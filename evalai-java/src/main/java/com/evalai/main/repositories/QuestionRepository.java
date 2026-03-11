package com.evalai.main.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.evalai.main.entities.QuestionEntity;
import com.evalai.main.entities.QuestionPaperEntity;

public interface QuestionRepository extends JpaRepository<QuestionEntity, String>{
	List<QuestionEntity> findByQuestionPaper(QuestionPaperEntity questionPaper);
	boolean existsByQuestionNumberAndQuestionPaper(Integer questionNumber,QuestionPaperEntity questionPaper);
}
