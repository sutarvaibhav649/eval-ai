package com.evalai.main.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.evalai.main.entities.AnswersheetEntity;
import com.evalai.main.entities.TaskLogsEntity;

public interface TaskLogsRepository extends JpaRepository<TaskLogsEntity, String> {

    Optional<TaskLogsEntity> findByTaskId(String taskId);

    List<TaskLogsEntity> findByAnswersheet(AnswersheetEntity answersheet);
}
