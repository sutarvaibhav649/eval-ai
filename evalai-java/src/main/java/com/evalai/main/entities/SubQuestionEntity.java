package com.evalai.main.entities;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.Id;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * The most granular level of an exam question.
 * Stores the actual text of the question, its specific weightage (marks), 
 * and a vector embedding of the question itself for context-aware grading.
 * 
 * @author Vaibhav Sutar
 * @version 1.0
 */

@Builder
@Entity
@Table(name = "sub_questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubQuestionEntity {
	
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "sub_question_id")
	private String id;
	
	@Column(name = "sub_question_label",nullable = false)
	private String subQuestionLabel;
	
	@Column(columnDefinition = "TEXT",nullable = false)
	private String questionText;
	
	@Column(name = "marks",nullable = false)
	private Float marks;
	
	@Column(name = "embedding",columnDefinition = "vector(384)")
	private float[] embedding;
	
	@Column(name = "created_at",nullable = false)
	@CreationTimestamp
	private LocalDateTime createdAt;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "question_id",nullable = false)
	private QuestionEntity question;
	
}
