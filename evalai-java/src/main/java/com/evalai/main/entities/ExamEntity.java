package com.evalai.main.entities;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;


import com.evalai.main.enums.ExamStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Represents a specific examination instance for a Subject.
 * Stores critical metadata such as total marks, duration, and the grievance deadline.
 * Serves as the parent for Question Papers and Answersheets.
 * @author Vaibhav Sutar
 * @version 1.0
 */

@Builder
@Entity
@Table(name = "exams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExamEntity {
	
	/**
     * Unique identifier for the exam record (UUID).
     */
	@Id
	@GeneratedValue(strategy =GenerationType.UUID)
	@Column(name = "exam_id")
	private String idString;
	
	/**
     * Title of the exam like MID-SEM-2025/26
     */
	@Column(name = "exam_title",nullable = false)
	private String titleString;
	
	
	@Column(name = "exam_date",nullable = false)
	private LocalDateTime examDateTime;
	
	@Column(name = "academic_year",nullable = false)
	private String academicYearString;
	
	@Column(name = "total_marks",nullable = false)
	private Integer totalMarksInteger;
	
	@Column(name = "exam_duration",nullable = false)
	private Integer durationInteger;
	
	@Builder.Default
	@Column(name = "status",nullable = false)
	@Enumerated(EnumType.STRING)
	private ExamStatus status = ExamStatus.SCHEDULED;
	
	@Column(name = "grievance_deadline",nullable = false)
	private LocalDateTime grievanceDeadlineDateTime;
	
	@Column(name = "created_at",nullable = false,updatable = false)
	@CreationTimestamp
	private LocalDateTime createDateTime;
	
	/**
     * Relation between the user and the exam who created the exam
     * Many to one relation between the user and exam
     * where many exams created by admin
     * Admin can create multiple exams
     */
	@ManyToOne
	@JoinColumn(name = "created_by", nullable = false)
	private UserEntity createdBy;
	
	/**
     * Relation between the subject and the exam
     * many to one relation between subject and exam
     * where multiple subjects are part of the one exam
     * Admin can create multiple exams
     */
	@ManyToOne
	@JoinColumn(name = "subject_id",nullable = false)
	private SubjectEntity subject;
}
