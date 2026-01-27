package com.example.SqlQuiz.repository;

import com.example.SqlQuiz.entity.QuizTableMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuizTableMetadataRepository extends JpaRepository<QuizTableMetadata, Long> {

    Optional<QuizTableMetadata> findByTablePrefix(String tablePrefix);

    List<QuizTableMetadata> findByQuestionId(Long questionId);

    List<QuizTableMetadata> findByCreatorIdAndIsActiveTrue(Long creatorId);

    boolean existsByTablePrefix(String tablePrefix);

    void deleteByQuestionId(Long questionId);
}
