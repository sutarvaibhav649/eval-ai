package com.evalai.main.entities;

import java.util.List;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Id;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * The "Gold Standard" answer provided by faculty for a specific SubQuestion.
 * Contains the reference text, its vector embedding, and a JSONB list of 
 * 'keyConcepts' that the student's answer must cover to receive full marks.
 * 
 * @author vaibhav sutar
 * @version 1.1
 */
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "model_answers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ModelAnswerEntity {
	
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "model_answer_id")
	private String id;
	
	@Column(name = "answer_text",columnDefinition = "TEXT",nullable = false)
	private String answerText;
	
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "key_concepts",columnDefinition = "jsonb")
	private List<String> keyConcepts;
	
	/**
	 *  Here the embedding are stored after the answer is uploaded
	 *  this is array of floating point values
	 */
	@Column(name = "embedding",columnDefinition = "vector(384)")
	private float[] embedding;
	
	@Column(name = "created_at",nullable = false, updatable = false)
	@CreationTimestamp
	private LocalDateTime createAt;
	
	@Column(name = "updated_at",nullable = false)
	@UpdateTimestamp
	private LocalDateTime updatedAt;
	
	/**
	 * Relation between the user and model answer
	 * one user can give many model answers
	 */
	@ManyToOne
	@JoinColumn(name = "user_id",nullable = false)
	private UserEntity user;
	
	/**
	 * relation between sub question and the model answers
	 * model answer has many sub questions 
	 */
	@OneToOne
	@JoinColumn(name = "sub_question_id",nullable = false)
	private SubQuestionEntity subQuestion;
}
