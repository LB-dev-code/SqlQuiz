package com.example.SqlQuiz;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Test Suite - Run all unit tests
 */
@Suite
@SuiteDisplayName("SQL Quiz Unit Test Suite")
@SelectClasses({
    // Entity class tests
    com.example.SqlQuiz.unit.entity.UserEntityTest.class,
    com.example.SqlQuiz.unit.entity.QuizEntityTest.class,
    com.example.SqlQuiz.unit.entity.QuestionEntityTest.class,
    com.example.SqlQuiz.unit.entity.SubmissionEntityTest.class,
    com.example.SqlQuiz.unit.entity.QuestionAnswerEntityTest.class,
    com.example.SqlQuiz.unit.entity.PracticeSessionEntityTest.class,
    com.example.SqlQuiz.unit.entity.PracticeRoundEntityTest.class,
    com.example.SqlQuiz.unit.entity.PracticeAnswerEntityTest.class,

    // Service class tests
    com.example.SqlQuiz.unit.service.UserServiceTest.class,
    com.example.SqlQuiz.unit.service.QuizServiceTest.class,
    com.example.SqlQuiz.unit.service.PracticeServiceTest.class,
    com.example.SqlQuiz.unit.service.QuestionDeduplicationServiceTest.class
})
public class UnitTestSuite {
}
