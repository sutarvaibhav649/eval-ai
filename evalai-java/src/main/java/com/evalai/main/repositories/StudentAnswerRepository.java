package com.evalai.main.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.evalai.main.entities.AnswersheetEntity;
import com.evalai.main.entities.StudentAnswer;
import com.evalai.main.entities.SubQuestionEntity;


public interface StudentAnswerRepository extends JpaRepository<StudentAnswer, String> {
	List<StudentAnswer> findByAnswersheet(AnswersheetEntity answersheet);
	Optional<StudentAnswer> findByAnswersheetAndSubQuestion(AnswersheetEntity answersheet, SubQuestionEntity subQuestion);
}
