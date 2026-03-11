package com.evalai.main.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.evalai.main.entities.QuestionEntity;
import com.evalai.main.entities.SubQuestionEntity;

public interface SubQuestionRepository extends JpaRepository<SubQuestionEntity, String>{
	List<SubQuestionEntity> findByQuestion(QuestionEntity question);
	boolean existsBySubQuestionLabelAndQuestion(String lable,QuestionEntity question);
}
