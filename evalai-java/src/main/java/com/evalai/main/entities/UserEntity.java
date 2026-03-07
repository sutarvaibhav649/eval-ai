package com.evalai.main.entities;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import jakarta.persistence.Id;

import com.evalai.main.enums.UserRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a user within the evaluation system.
 * This includes Students, Faculty, and Administrators.
 * Manages authentication, role-based access control, and departmental affiliation.
 * @author Vaibhav Sutar
 * @version 1.1
 */
@Builder
@Entity
@Table(name = "users", indexes = {
		 @Index(name = "idx_user_email", columnList = "user_email")
	})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserEntity {
	
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "user_id")
	private String id;
	
	@Column(name = "user_name",length = 255,nullable = false)
	private String nameString;
	
	@Column(name = "user_email",length = 255,nullable = false,unique = true)
	@Email(message = "Enter valid email")
	private String emailString;
	
	@Column(name = "user_password",nullable = false)
	private String passwordString;
	
	@Column(name = "user_role",nullable = false)
	@Enumerated(EnumType.STRING)
	private UserRole role;
	
	@Column(name = "user_department",nullable = false)
	private String departmentString;
	
	@Builder.Default
	@Column(name = "is_active",nullable = false)
	private Boolean isActiveBoolean = true;
	
	@Column(name = "created_at",nullable = false,updatable = false)
	@CreationTimestamp
	private LocalDateTime createAtDateTime;
	
	@Column(name = "updated_at",nullable = false)
	@UpdateTimestamp
	private LocalDateTime updatedAtDateTime;
}
