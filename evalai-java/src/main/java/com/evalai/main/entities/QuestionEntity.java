package com.evalai.main.entities;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.Id;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Represents a primary question within a Question Paper.
 * Tracks the question number and the aggregate marks assigned to it.
 * @author Vaibhav Sutar
 * @version 1.0
 */

@Builder
@Entity
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuestionEntity {
	
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "question_id")
	private String idString;
	
	@Column(name = "question_number",nullable = false)
	private Integer questionNumberInteger;
	
	@Column(name = "total_marks",nullable = false)
	private Float totalMarksFloat;
	
	@Column(name = "created_at",nullable = false)
	@CreationTimestamp
	private LocalDateTime createdAtDateTime;
	
	@ManyToOne
	@JoinColumn(name = "question_paper_id",nullable = false)
	private QuestionPaperEntity questionPaper;
}
