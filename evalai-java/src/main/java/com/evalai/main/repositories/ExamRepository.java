package com.evalai.main.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.evalai.main.entities.ExamEntity;
import com.evalai.main.entities.SubjectEntity;
import com.evalai.main.entities.UserEntity;

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

 // Remove @Override — findById is already inherited from JpaRepository
    List<ExamEntity> findBySubject(SubjectEntity subject);
    List<ExamEntity> findByCreatedBy(UserEntity createdBy);
    boolean existsByTitleAndSubject_Id(String title, String subjectId);
}
