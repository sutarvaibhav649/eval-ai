package com.evalai.main.entities;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import jakarta.persistence.Id;

import com.evalai.main.enums.TaskLogStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Provides transparency for asynchronous background processes.
 * Links the Java backend with Python Celery workers for heavy tasks like 
 * OCR and Embedding generation, storing internal task IDs and error messages.
 * 
 * @author Vaibhav Sutar
 * @version 1.0
 */

@Builder
@Entity
@Table(name = "task_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TaskLogsEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "task_log_id")
    private String id;

    @Column(name = "task_id", nullable = false, unique = true)
    private String taskId;
    
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private TaskLogStatus status = TaskLogStatus.QUEUED;
    
    @Column(name = "celery_internal_id")
    private String celeryTaskId;
    
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    
    @UpdateTimestamp 
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @ManyToOne(fetch = FetchType.LAZY) 
    @JoinColumn(name = "answersheet_id", nullable = false)
    private AnswersheetEntity answersheet;
}
