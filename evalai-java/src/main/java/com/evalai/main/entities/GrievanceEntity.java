package com.evalai.main.entities;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import jakarta.persistence.Id;
import com.evalai.main.enums.GrievanceStatus;
import com.evalai.main.enums.GrievanceType;

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
 * Manages student-initiated disputes regarding marks awarded.
 * Tracks the reason for the grievance, requested marks, and the final 
 * decision made by a human reviewer.
 * </p>
 */
@Builder
@Entity
@Table(name = "grievances")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GrievanceEntity {
	
	/**
     * Unique identifier for the grievance record (UUID).
     */
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "grievance_id")
	private String id;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "grievance_type", nullable = false)
	private GrievanceType grievanceType;
	
	/**
	 * This tells the students reason for this grievance
	 */
	@Column(name = "student_reason", columnDefinition = "TEXT")
	private String studentReason;
	
	/**
	 * This tells the failure reason for the evaluation
	 * by model
	 */
	@Column(name = "failure_reason", columnDefinition = "TEXT")
	private String failureReason;
	
	@Builder.Default
	@Enumerated(EnumType.STRING)
	@Column(name = "status")
	private GrievanceStatus status = GrievanceStatus.PENDING;
	
	/**
	 * This tells requested marks from the students of the evaluation 
	 */
	@Column(name = "requested_marks")
	private Float requestedMarks;
	
	/**
	 * This tells the awarded marks for this grievance by the faculty 
	 * after reviewing the answersheet and that question
	 */
	@Column(name = "awarded_marks")
	private Float awardedMarks;
	
	/**
	 * This is the faculty's comment on the grievance weather the grievance
	 * is resolved or the marks are same
	 */
	@Column(name = "reviewer_comment", columnDefinition = "TEXT")
	private String reviewerComment;
	
	@CreationTimestamp
	@Column(name = "raised_at", nullable = false, updatable = false)
	private LocalDateTime raisedAt;
	
	@Column(name = "resolved_at")
	private LocalDateTime resolvedAt;
	
	/**
	 * Relation between the result and the grievances
	 * There are many grievances for the result
	 */
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "result_id", nullable = false, unique = true)
	private ResultEntity result;
	
	/**
	 * Relation between the student and the grievances
	 * There are many grievances for the result
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "student_id", nullable = false)
	private UserEntity student;
	
	/**
	 * Relation between the faculty and the grievances
	 * who reviewed the grievance
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reviewed_by_id")
	private UserEntity reviewedBy;
}