package com.example.SqlQuiz.core;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
        AIQuestionGenerationTest.class,
        AINormalizationTest.class,
        AIScoringTest.class,
        PracticeServiceCoreTest.class,
        SqlValidatorTest.class
})
class CoreFeatureTestSuite {
}
