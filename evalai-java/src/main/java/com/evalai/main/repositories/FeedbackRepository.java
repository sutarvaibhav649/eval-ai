package com.evalai.main.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.evalai.main.entities.AnswersheetEntity;
import com.evalai.main.entities.FeedbackEntity;
import com.evalai.main.entities.ResultEntity;

public interface FeedbackRepository extends JpaRepository<FeedbackEntity, String>{
	List<FeedbackEntity> findByAnswersheet(AnswersheetEntity answersheet);
	List<FeedbackEntity> findByResult(ResultEntity result);
}
