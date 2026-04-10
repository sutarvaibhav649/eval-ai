package com.evalai.main.entities;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import com.evalai.main.enums.ExamStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * FIX: ExamEntity — changed subject relation from ManyToOne → ManyToMany.
 *
 * REASON: Business logic says "one exam can have many subjects"
 * (e.g., a combined mid-sem exam covering multiple subjects).
 * ManyToMany uses a join table: exam_subjects(exam_id, subject_id).
 *
 * IMPACT:
 *  - ExamRequestDTO now takes List<String> subjectIds instead of String subjectId
 *  - AdminService.createExam() updated to fetch all subjects and set the list
 *  - ExamResponseDTO returns List<SubjectResponseDTO> instead of single subject
 *  - QuestionPaperEntity still links to one exam (unchanged)
 */
@Builder
@Entity
@Table(name = "exams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExamEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "exam_id")
    private String id;

    @Column(name = "exam_title", nullable = false)
    private String title;

    @Column(name = "exam_date", nullable = false)
    private LocalDateTime examDate;

    @Column(name = "academic_year", nullable = false)
    private String academicYear;

    @Column(name = "total_marks", nullable = false)
    private Integer totalMarks;

    @Column(name = "exam_duration", nullable = false)
    private Integer duration;

    @Builder.Default
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private ExamStatus status = ExamStatus.SCHEDULED;

    @Column(name = "grievance_deadline", nullable = false)
    private LocalDateTime grievanceDeadline;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    /**
     * Many exams created by one admin.
     */
    @ManyToOne
    @JoinColumn(name = "created_by", nullable = false)
    private UserEntity createdBy;

    /**
     * FIX: Was @ManyToOne → single subject.
     * Now @ManyToMany → one exam covers multiple subjects.
     *
     * Join table: exam_subjects
     *   exam_id     FK → exams.exam_id
     *   subject_id  FK → subjects.subject_id
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "exam_subjects",
            joinColumns = @JoinColumn(name = "exam_id"),
            inverseJoinColumns = @JoinColumn(name = "subject_id")
    )
    @Builder.Default
    private List<SubjectEntity> subjects = new ArrayList<>();
}