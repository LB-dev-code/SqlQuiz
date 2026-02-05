package com.example.SqlQuiz.repository;

import com.example.SqlQuiz.entity.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findFirstByEmailOrderByCreatedAtDesc(String email);

    void deleteByEmail(String email);
}
