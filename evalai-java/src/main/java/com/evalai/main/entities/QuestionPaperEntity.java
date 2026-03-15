package com.evalai.main.entities;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import jakarta.persistence.Id;

import com.evalai.main.enums.QuestionPaperStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Represents the physical or digital document containing examination questions.
 * Manages the status of the paper (DRAFT, PUBLISHED) and tracks similarity checks 
 * performed against model answers to ensure academic integrity.
 * 
 * @author Vaibhav Sutar
 * @version 1.0
 */

@Builder
@Entity
@Table(name = "question_papers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuestionPaperEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "question_paper_id")
	private String id;
	
	@Column(name = "title",nullable = false)
	private String title;
	
	@Column(name = "set_label",nullable = false)
	private String setLable;
	
	@Column(name = "file_path",nullable = false)
	private String filePath;
	
	@Builder.Default
	@Column(name = "status",nullable = false)
	@Enumerated(EnumType.STRING)
	private QuestionPaperStatus status = QuestionPaperStatus.DRAFT;
	
	@Builder.Default
	@Column(name = "similarity_checked",nullable = false)
	private Boolean similarityChecked = false;
	
	@Column(name = "created_at",nullable = false)
	@CreationTimestamp
	private LocalDateTime createAt;
	
	@Column(name = "updated_at",nullable = false)
	@UpdateTimestamp
	private LocalDateTime updatedAt;

	
	@ManyToOne
	@JoinColumn(name = "exam_id",nullable = false)
	private ExamEntity exam;
	
	@ManyToOne
	@JoinColumn(name = "faculty_id",nullable = false)
	private UserEntity faculty;
	
	@OneToMany(mappedBy = "questionPaper", fetch = FetchType.LAZY)
	@JsonIgnore
	private List<QuestionEntity> questions = new ArrayList<>();
}
