package com.evalai.main.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.evalai.main.entities.ExamEntity;
import com.evalai.main.entities.SubjectEntity;
import com.evalai.main.entities.UserEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * This interface serves as the data access layer for ExamEntity, providing
 * methods to perform CRUD operations and custom queries on the exams table in
 * the database. It extends JpaRepository, which offers built-in methods for
 * common database interactions, and also defines custom query methods to find
 * exams by title and exam date. This repository is essential for managing exam
 * records and retrieving exam information based on specific criteria.
 *
 * @author Vaibhav Sutar
 * @version 1.0
 */
public interface ExamRepository extends JpaRepository<ExamEntity, String> {

    List<ExamEntity> findByCreatedBy(UserEntity createdBy);

    /**
     * FIX: was findBySubject(SubjectEntity) — single subject.
     * Now finds all exams that contain a specific subject in their ManyToMany list.
     */
    @Query("SELECT e FROM ExamEntity e JOIN e.subjects s WHERE s = :subject")
    List<ExamEntity> findBySubject(@Param("subject") SubjectEntity subject);

    /**
     * FIX: was existsByTitleAndSubject_Id(title, subjectId).
     * Now checks if title already exists for this exam creator
     * (duplicate title check — across subjects doesn't make sense anymore).
     *
     * If you still need title+subject uniqueness: use the JPQL below.
     */
    boolean existsByTitleAndCreatedBy(String title, UserEntity createdBy);

    /**
     * Alternative: check if an exam with this title already covers one of these subjects.
     * Use this if business rule is "no duplicate exam title per subject".
     */
    @Query("SELECT COUNT(e) > 0 FROM ExamEntity e JOIN e.subjects s " +
            "WHERE e.title = :title AND s.id IN :subjectIds")
    boolean existsByTitleAndAnySubjectId(
            @Param("title") String title,
            @Param("subjectIds") List<String> subjectIds
    );
}
