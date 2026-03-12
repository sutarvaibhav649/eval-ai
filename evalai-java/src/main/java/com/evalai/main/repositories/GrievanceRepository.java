package com.evalai.main.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.evalai.main.entities.ExamEntity;
import com.evalai.main.entities.GrievanceEntity;
import com.evalai.main.entities.UserEntity;
import com.evalai.main.entities.ResultEntity;
import com.evalai.main.enums.GrievanceStatus;
import com.evalai.main.enums.GrievanceType;

public interface GrievanceRepository extends JpaRepository<GrievanceEntity, String>{
	List<GrievanceEntity> findByStudent(UserEntity student);
	List<GrievanceEntity> findByResult(ResultEntity result);
	List<GrievanceEntity> findByStatus(GrievanceStatus status);
	boolean existsByStudentAndResult(UserEntity student, ResultEntity result);
	
	List<GrievanceEntity> findByStudentAndResult_Answersheet_Exam(UserEntity student, ExamEntity exam);
	boolean existsByResultAndGrievanceType(ResultEntity result, GrievanceType grievanceType);
}
