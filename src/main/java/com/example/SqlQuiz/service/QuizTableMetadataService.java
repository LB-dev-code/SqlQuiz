package com.example.SqlQuiz.service;

import com.example.SqlQuiz.entity.QuizTableMetadata;
import com.example.SqlQuiz.repository.QuizTableMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class QuizTableMetadataService {

    @Autowired
    private QuizTableMetadataRepository metadataRepository;

    public String generateUniqueTablePrefix() {
        String prefix;
        int attempts = 0;
        int maxAttempts = 10;

        do {
            if (attempts >= maxAttempts) {
                throw new RuntimeException("Failed to generate unique table prefix after " + maxAttempts + " attempts");
            }

            prefix = "quiz_q_" +
                    UUID.randomUUID().toString().substring(0, 8) + "_" +
                    System.currentTimeMillis();
            attempts++;
        } while (metadataRepository.existsByTablePrefix(prefix));

        return prefix;
    }

    public QuizTableMetadata createMetadata(String tablePrefix, Long questionId, Long creatorId) {
        if (metadataRepository.existsByTablePrefix(tablePrefix)) {
            throw new RuntimeException("Table prefix already exists: " + tablePrefix);
        }

        QuizTableMetadata metadata = new QuizTableMetadata(tablePrefix, questionId, creatorId);
        return metadataRepository.save(metadata);
    }

    public Optional<QuizTableMetadata> getByTablePrefix(String tablePrefix) {
        return metadataRepository.findByTablePrefix(tablePrefix);
    }

    public List<QuizTableMetadata> getByQuestionId(Long questionId) {
        return metadataRepository.findByQuestionId(questionId);
    }

    public List<QuizTableMetadata> getActiveByCreatorId(Long creatorId) {
        return metadataRepository.findByCreatorIdAndIsActiveTrue(creatorId);
    }

    public void deactivateByQuestionId(Long questionId) {
        List<QuizTableMetadata> metadataList = metadataRepository.findByQuestionId(questionId);
        metadataList.forEach(metadata -> metadata.setIsActive(false));
        metadataRepository.saveAll(metadataList);
    }

    public void deleteByQuestionId(Long questionId) {
        metadataRepository.deleteByQuestionId(questionId);
    }

    public QuizTableMetadata updateQuestionId(String tablePrefix, Long questionId) {
        Optional<QuizTableMetadata> metadataOpt = metadataRepository.findByTablePrefix(tablePrefix);
        if (metadataOpt.isPresent()) {
            QuizTableMetadata metadata = metadataOpt.get();
            metadata.setQuestionId(questionId);
            return metadataRepository.save(metadata);
        }
        throw new RuntimeException("Table metadata not found: " + tablePrefix);
    }
}
