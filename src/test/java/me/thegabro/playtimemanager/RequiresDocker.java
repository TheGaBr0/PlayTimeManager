package me.thegabro.playtimemanager;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test class that needs Docker (real database servers). Without Docker the whole class is
 * skipped before anything starts, so it costs nothing and does not fail the build.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@ExtendWith(RequiresDocker.Condition.class)
@interface RequiresDocker {

    class Condition implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            return TestDatabases.dockerAvailable()
                    ? ConditionEvaluationResult.enabled("Docker is available")
                    : ConditionEvaluationResult.disabled("Docker is not available");
        }
    }
}
