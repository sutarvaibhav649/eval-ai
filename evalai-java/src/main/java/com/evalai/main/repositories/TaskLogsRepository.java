package com.evalai.main.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.evalai.main.entities.AnswersheetEntity;
import com.evalai.main.entities.TaskLogsEntity;

import jakarta.persistence.LockModeType;

public interface TaskLogsRepository extends JpaRepository<TaskLogsEntity, String> {

    Optional<TaskLogsEntity> findByTaskId(String taskId);

    Optional<TaskLogsEntity> findByAnswersheet(AnswersheetEntity answersheet);
    
 // ADD THIS METHOD (IMPORTANT)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TaskLogsEntity t WHERE t.answersheet = :answersheet")
    Optional<TaskLogsEntity> findByAnswersheetForUpdate(
            @Param("answersheet") AnswersheetEntity answersheet
    );
    
}
