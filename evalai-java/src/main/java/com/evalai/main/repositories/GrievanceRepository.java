package com.evalai.main.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.evalai.main.entities.GrievanceEntity;
import com.evalai.main.entities.UserEntity;
import com.evalai.main.entities.ResultEntity;
import com.evalai.main.enums.GrievanceStatus;

public interface GrievanceRepository extends JpaRepository<GrievanceEntity, String>{
	List<GrievanceEntity> findByStudent(UserEntity student);
	List<GrievanceEntity> findByResult(ResultEntity result);
	List<GrievanceEntity> findByStatus(GrievanceStatus status);
	boolean existsByStudentAndResult(UserEntity student, ResultEntity result);
}
