package com.evalai.main.entities;

import java.time.LocalDateTime;
import java.util.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Id;

import com.evalai.main.enums.FeedbackGeneratedBy;

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
 * Stores qualitative, AI-generated insights for a student's answer.
 * Uses Gemini to identify strengths, weaknesses, and specific 'keyConceptsMissed' 
 * based on the comparison between the StudentAnswer and ModelAnswer.
 * 
 * @author Vaibhav Sutar
 * @version 1.0
 */
@Builder
@Entity
@Table(name = "feedbacks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackEntity {

	/**
     * Unique identifier for the feedback record (UUID).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "feedback_id")
    private String id;

    /**
     * This tells the strengths of the student in the paper
     */
    @Column(name = "strengths", columnDefinition = "TEXT", nullable = false)
    private String strengths;

    /**
     * This tells the weaknesses of the student in the paper
     */
    @Column(name = "weakness", columnDefinition = "TEXT", nullable = false)
    private String weakness;

    /**
     * This gives the suggestions to the student related to there 
     * answer writing in the exam what to improve,etc
     */
    @Column(name = "suggestions", columnDefinition = "TEXT", nullable = false)
    private String suggestions;

    /**
     * This is the array of key concepts must be present in the answer 
     * based on this marks may vary
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_concepts_missed", columnDefinition = "jsonb", nullable = false)
    private List<String> keyConceptsMissed;

    /**
     * It gives the overall feedback about the result
     */
    @Column(name = "overall_feedback", columnDefinition = "TEXT")
    private String overallFeedback;

    /**
     * Which model the system uses for the feed back generation 
     * GEMINI or the LOCAL model
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "generated_by")
    private FeedbackGeneratedBy generatedBy = FeedbackGeneratedBy.GEMINI;

    @CreationTimestamp 
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Relation between result and the feedback
     * one result have one feedbacks to the sub questions or the questions
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_id", nullable = false, unique = true)
    private ResultEntity result;

    /**
     * Relation between answersheet and the feedback
     * one answersheet have many feedbacks to the sub questions or the questions
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answersheet_id", nullable = false)
    private AnswersheetEntity answersheet;
}
