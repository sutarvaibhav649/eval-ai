package com.evalai.main.entities;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import jakarta.persistence.Id;

import com.evalai.main.enums.ResultStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Stores the evaluation outcome for a specific StudentAnswer.
 * Bridges AI-calculated marks (based on vector similarity) with the final awarded marks.
 * Supports human-in-the-loop overrides by tracking the reviewer and the reason for change.
 * @author Vaibhav Sutar
 * @version 1.0
 */

@Builder
@Entity
@Table(name = "results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResultEntity {
	
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "result_id")
	private String id; 
	
	@Builder.Default
	@Enumerated(EnumType.STRING)
	@Column(name = "result_status", nullable = false)
	private ResultStatus status = ResultStatus.COMPLETED;
	
	@Column(name = "ai_marks", nullable = false)
	private Float aiMarks;
	
	@Column(name = "total_marks", nullable = false)
	private Float totalMarks;
	
	@Column(name = "similarity_score")
	private Float similarityScore;
	
	@Builder.Default
	@Column(name = "is_overriden", nullable = false)
	private Boolean isOverriden = false;
	
	@Column(name = "final_marks", nullable = false)
	private Float finalMarks;

	@Column(name = "override_reason", columnDefinition = "TEXT")
	private String overrideReason;
	
	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	
	@UpdateTimestamp
	@Column(name = "overriden_at")
	private LocalDateTime overridenAt;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "answersheet_id", nullable = false)
	private AnswersheetEntity answersheet;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "student_id", nullable = false) 
	private UserEntity student;
	
	@OneToOne
	@JoinColumn(name = "student_answer_id", nullable = false)
	private StudentAnswer studentAnswer;
	
	@ManyToOne(fetch = FetchType.LAZY) 
	@JoinColumn(name = "overriden_by_id")
	private UserEntity overridenBy;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "sub_question_id", nullable = false)
	private SubQuestionEntity subQuestion;
}

