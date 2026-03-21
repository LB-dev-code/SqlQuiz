package com.example.SqlQuiz;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Test Suite - Run all integration tests
 */
@Suite
@SuiteDisplayName("SQL Quiz Integration Test Suite")
@SelectClasses({
    // Controller integration tests
    com.example.SqlQuiz.integration.controller.AuthControllerIntegrationTest.class,
    com.example.SqlQuiz.integration.controller.TeacherControllerIntegrationTest.class,
    com.example.SqlQuiz.integration.controller.StudentControllerIntegrationTest.class,
    com.example.SqlQuiz.integration.controller.SqlPracticeControllerIntegrationTest.class,

    // Repository integration tests
    com.example.SqlQuiz.integration.repository.UserRepositoryIntegrationTest.class,
    com.example.SqlQuiz.integration.repository.QuizRepositoryIntegrationTest.class,
    com.example.SqlQuiz.integration.repository.QuestionRepositoryIntegrationTest.class,
    com.example.SqlQuiz.integration.repository.SubmissionRepositoryIntegrationTest.class,
    com.example.SqlQuiz.integration.repository.PracticeSessionRepositoryIntegrationTest.class
})
public class IntegrationTestSuite {
}
