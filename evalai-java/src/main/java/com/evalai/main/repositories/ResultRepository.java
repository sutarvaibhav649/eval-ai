package com.evalai.main.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.evalai.main.entities.AnswersheetEntity;
import com.evalai.main.entities.ResultEntity;
import com.evalai.main.entities.SubQuestionEntity;
import com.evalai.main.entities.UserEntity;


public interface ResultRepository extends JpaRepository<ResultEntity, String>{
	List<ResultEntity> findByAnswersheet(AnswersheetEntity answersheet);
	List<ResultEntity> findByStudent(UserEntity student);
	Optional<ResultEntity> findBySubQuestion(SubQuestionEntity subQuestion);
}
