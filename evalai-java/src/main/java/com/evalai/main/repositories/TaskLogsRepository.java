package com.evalai.main.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.evalai.main.entities.AnswersheetEntity;
import com.evalai.main.entities.TaskLogsEntity;

public interface TaskLogsRepository extends JpaRepository<TaskLogsEntity, String> {

    Optional<TaskLogsEntity> findByTaskId(String taskId);

    Optional<TaskLogsEntity> findByAnswersheet(AnswersheetEntity answersheet);
    
    
}
