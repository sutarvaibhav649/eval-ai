package com.evalai.main.entities;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Id;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
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
	private String id;
	
	@Column(name = "question_number",nullable = false)
	private Integer questionNumber;
	
	@Column(name = "total_marks",nullable = false)
	private Float totalMarks;
	
	@Column(name = "created_at",nullable = false)
	@CreationTimestamp
	private LocalDateTime createdAt;
	
	@ManyToOne
	@JoinColumn(name = "question_paper_id",nullable = false)
	private QuestionPaperEntity questionPaper;
	
	@OneToMany(mappedBy = "question", fetch = FetchType.LAZY)
	@JsonIgnore
	private List<SubQuestionEntity> subQuestions = new ArrayList<>();
}
