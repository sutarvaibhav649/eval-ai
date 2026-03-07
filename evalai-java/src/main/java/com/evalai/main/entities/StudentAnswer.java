package com.evalai.main.entities;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.Id;

import com.evalai.main.enums.FailureReason;

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
 * Stores the OCR-extracted response for a specific SubQuestion.
 * Contains the raw 'extractedText' and 'cleanedText'.
 * Holds the 384-dimensional vector embedding used for semantic similarity comparison.
 * *@author Vaibhav Sutar
 * 	@version 1.0
 */
@Builder
@Entity
@Table(name = "student_answers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StudentAnswer {
	
	/**
     * Unique identifier for the student answer record (UUID).
     */
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "student_answers_id")
	private String idString;
	
	/**
	 * Raw extracted text directly from the answer sheet via OCR
	 */
	@Column(name = "extracted_text",columnDefinition = "TEXT",nullable = false)
	private String extractedTextString;
	
	/**
	 * Cleaned text from the extracted text with the help of model answer
	 */
	@Column(name = "cleaned_text",columnDefinition = "TEXT",nullable = true)
	private String cleanedTextString;
	
	
	/**
	 * floating point values of the extracted text with 384 dimensions
	 */
	@Column(name = "embedding",columnDefinition = "vector(384)")
	private float[] embedding;
	
	/**
	 * floating point value of the OCR model confidence about the extracted text
	 * confidence score is from 0.0 to 1.0
	 */
	@Builder.Default
	@Column(name = "ocr_confidence")
	private Float ocrConfidenceFloat = 0.0f;
	
	/**
	 * Reason for the Failure of the OCR by the model
	 */
	@Column(name = "failure_reason",nullable = true)
	@Enumerated(EnumType.STRING)
	private FailureReason failureReason;
	
	/**
	 * When the student answer is being extracted
	 */
	@Column(name = "created_at",nullable = false)
	@CreationTimestamp
	private LocalDateTime createdAtDateTime;
	
	/**
	 * Relation between Answersheet and the StudentAnswers
	 * The relation is many to one
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "answersheet_id",nullable = false)
	private AnswersheetEntity answersheet;
	
	/**
	 * Relation between Sub questions and the StudentAnswers
	 * The relation is many to one
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "sub_question_id", nullable = false)
	private SubQuestionEntity subQuestion;
}
