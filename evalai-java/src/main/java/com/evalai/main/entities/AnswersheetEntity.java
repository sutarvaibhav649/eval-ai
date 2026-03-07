package com.evalai.main.entities;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.evalai.main.enums.EvaluationStatus;
import com.evalai.main.enums.OcrStatus;

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
 * Represents a student's complete exam submission (e.g., a scanned PDF).
 * Tracks the overall OCR processing status, total marks awarded after evaluation, 
 * and identifies both the student and the uploader.
 * @author Vaibhav Sutar
 * @version 1.0
 */
@Builder
@Entity
@Table(name = "answersheets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AnswersheetEntity {
	
	/**
     * Unique identifier for the student answer record (UUID).
     */	
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "answersheet_id")
	private String idString;
	
	/**
	 * File Path of the uploaded answersheet
	 */
	@Column(name = "file_path",nullable = false)
	private String filePathString;
	
	/**
	 * File type of the uploaded answer sheets
	 */
	@Column(name = "file_type",nullable = false)
	private String fileTypeString;
	
	/**
	 * OCR status to check is OCR done or pending of the answer sheet
	 * default is pending
	 */
	@Builder.Default
	@Column(name = "ocr_status",nullable = false)
	@Enumerated(EnumType.STRING)
	private OcrStatus ocrStatus = OcrStatus.PENDING;
	
	/**
	 * Evaluation status of the answersheet after the OCR done
	 * default pending
	 */
	@Builder.Default
	@Column(name = "evaluation_status",nullable = false)
	@Enumerated(EnumType.STRING)
	private EvaluationStatus evaluationStatus = EvaluationStatus.PENDING;
	
	/**
	 * Total marks of of the answer sheet in floating point
	 */
	@Column(name = "marks")
	private Float marksFloat;
	
	/**
	 * Time when the answersheet is uploaded
	 */
	@Column(name = "uploaded_at",nullable = false,updatable = false)
	@CreationTimestamp
	private LocalDateTime uploadedAtDateTime;
	
	/**
	 * Time when the answersheet is evaluated
	 */
	@Column(name = "evaluated_at", nullable = true)
	private LocalDateTime evaluatedAtDateTime;
	
	/**
	 * This relation for between exam and the answersheet where 
	 * one exam have multiple answersheets
	 */
	@ManyToOne
	@JoinColumn(name = "exam_id",nullable = false)
	private ExamEntity exam;
	
	
	/**
	 * This relation for between user and the answersheet where 
	 * one student have multiple answersheets
	 */
	@ManyToOne
	@JoinColumn(name = "user_id",nullable = false)
	private UserEntity student;
	
	/**
	 * This relation for between user and the answersheet where 
	 * one user can upload multiple answersheets
	 */
	@ManyToOne
	@JoinColumn(name = "uploaded_by",nullable = false)
	private UserEntity uploadedBy;
}
