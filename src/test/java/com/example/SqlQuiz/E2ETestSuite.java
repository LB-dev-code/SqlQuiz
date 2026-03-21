package com.example.SqlQuiz;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Test Suite - Run all end-to-end tests
 */
@Suite
@SuiteDisplayName("SQL Quiz End-to-End Test Suite")
@SelectClasses({
    com.example.SqlQuiz.e2e.QuizFlowE2ETest.class,
    com.example.SqlQuiz.e2e.PracticeFlowE2ETest.class
})
public class E2ETestSuite {
}
