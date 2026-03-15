package com.evalai.main.repositories;

//import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.evalai.main.entities.ModelAnswerEntity;
import com.evalai.main.entities.SubQuestionEntity;

public interface ModelAnswerRepository extends JpaRepository<ModelAnswerEntity, String>{
	Optional<ModelAnswerEntity> findBySubQuestion(SubQuestionEntity subQuestion);
	boolean existsBySubQuestion(SubQuestionEntity subQuestion);
}
