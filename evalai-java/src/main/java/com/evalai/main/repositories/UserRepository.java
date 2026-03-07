package com.evalai.main.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.evalai.main.entities.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, String>{
	Optional<UserEntity> findByEmail(String email);
}
