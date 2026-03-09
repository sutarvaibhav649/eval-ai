package com.evalai.main.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.evalai.main.entities.SubjectEntity;

public interface SubjectRepository extends JpaRepository<SubjectEntity, String> {

    boolean existsByCode(String code);

    Optional<SubjectEntity> findByCode(String code);
}
