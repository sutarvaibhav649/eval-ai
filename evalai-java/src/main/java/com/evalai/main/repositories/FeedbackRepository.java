package com.evalai.main.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.evalai.main.entities.AnswersheetEntity;
import com.evalai.main.entities.ExamEntity;
import com.evalai.main.entities.FeedbackEntity;
import com.evalai.main.entities.ResultEntity;
import com.evalai.main.entities.UserEntity;

public interface FeedbackRepository extends JpaRepository<FeedbackEntity, String>{
	List<FeedbackEntity> findByAnswersheet(AnswersheetEntity answersheet);
	List<FeedbackEntity> findByResult(ResultEntity result);
	
	List<FeedbackEntity> findByAnswersheet_ExamAndAnswersheet_Student(
		    ExamEntity exam, UserEntity student
		);
}
