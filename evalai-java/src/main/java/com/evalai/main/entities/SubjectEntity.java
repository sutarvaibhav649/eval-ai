package com.evalai.main.entities;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Defines a specific academic course or subject. Acts as the top-level
 * container for Exams and defines the department and semester context.
 *
 * @author Vaibhav Sutar
 * @version 1.1
 */
@Builder
@Entity
@Table(name = "subjects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubjectEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "subject_id")
    private String id;

    @Column(name = "subject_name", nullable = false, unique = true)
    private String name;

    @Column(name = "subject_code", nullable = false, unique = true)
    private String code;

    @Column(name = "subject_department", nullable = false)
    private String department;

    @Column(name = "subject_semester", nullable = false)
    private Integer semester;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

}
