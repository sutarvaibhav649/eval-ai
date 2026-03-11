package com.evalai.main.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.evalai.main.entities.ModelAnswerEntity;
import com.evalai.main.entities.SubQuestionEntity;

public interface ModelAnswerRepository extends JpaRepository<ModelAnswerEntity, String>{
	List<ModelAnswerEntity> findBySubQuestion(SubQuestionEntity subQuestion);
	boolean existsBySubQuestion(SubQuestionEntity subQuestion);
}
