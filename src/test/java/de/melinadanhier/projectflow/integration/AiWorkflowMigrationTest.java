package de.melinadanhier.projectflow.integration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiWorkflowMigrationTest {

    @Test
    void migratesLegacyErrorCodesAndSeparatesOperation() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:ai-error-operation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "")) {
            UUID preCheckFailure = UUID.randomUUID();
            UUID incompleteGeneration = UUID.randomUUID();
            UUID unavailableGeneration = UUID.randomUUID();
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE ai_plan_generation_workflows (
                            id UUID PRIMARY KEY,
                            pre_check_result VARCHAR(1000),
                            last_technical_error VARCHAR(50),
                            last_error_retryable BOOLEAN,
                            last_error_diagnosis VARCHAR(500),
                            CONSTRAINT ck_ai_workflows_technical_error CHECK (
                                last_technical_error IS NULL OR last_technical_error IN (
                                    'PROVIDER_UNAVAILABLE', 'CLIENT_CONFIGURATION_ERROR',
                                    'INVALID_AI_RESPONSE', 'AI_REFUSAL', 'INCOMPLETE_AI_RESPONSE',
                                    'PRE_CHECK_INITIALIZATION_FAILED', 'PRE_CHECK_PROCESSING_FAILED',
                                    'RETRY_INTERRUPTED', 'UNKNOWN_AI_ERROR'))
                        )
                        """);
                statement.execute("""
                        INSERT INTO ai_plan_generation_workflows (
                            id, pre_check_result, last_technical_error,
                            last_error_retryable, last_error_diagnosis
                        )
                        VALUES
                            ('%s', NULL, 'PRE_CHECK_PROCESSING_FAILED', NULL, 'Veraltet'),
                            ('%s', '{}', 'INCOMPLETE_AI_RESPONSE', TRUE, 'Veraltet'),
                            ('%s', '{}', 'PROVIDER_UNAVAILABLE', FALSE, 'Veraltet')
                        """.formatted(preCheckFailure, incompleteGeneration, unavailableGeneration));
            }

            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/V14__separate_ai_error_operation.sql"));

            try (var statement = connection.createStatement();
                 var results = statement.executeQuery("""
                         SELECT id, last_technical_error, last_ai_operation,
                                last_error_retryable, last_error_diagnosis
                         FROM ai_plan_generation_workflows
                         ORDER BY id
                         """)) {
                java.util.Map<UUID, String> codes = new java.util.HashMap<>();
                java.util.Map<UUID, String> operations = new java.util.HashMap<>();
                java.util.Map<UUID, Boolean> retryable = new java.util.HashMap<>();
                java.util.Map<UUID, String> diagnoses = new java.util.HashMap<>();
                while (results.next()) {
                    UUID id = results.getObject("id", UUID.class);
                    codes.put(id, results.getString("last_technical_error"));
                    operations.put(id, results.getString("last_ai_operation"));
                    retryable.put(id, results.getBoolean("last_error_retryable"));
                    diagnoses.put(id, results.getString("last_error_diagnosis"));
                }
                assertThat(codes.get(preCheckFailure)).isEqualTo("UNKNOWN_AI_ERROR");
                assertThat(operations.get(preCheckFailure)).isEqualTo("PRE_CHECK");
                assertThat(retryable.get(preCheckFailure)).isFalse();
                assertThat(diagnoses.get(preCheckFailure))
                        .isEqualTo("Die KI-Verarbeitung ist an einem internen technischen Fehler gescheitert.");
                assertThat(codes.get(incompleteGeneration)).isEqualTo("INVALID_AI_RESPONSE");
                assertThat(operations.get(incompleteGeneration)).isEqualTo("PLAN_GENERATION");
                assertThat(retryable.get(incompleteGeneration)).isFalse();
                assertThat(diagnoses.get(incompleteGeneration))
                        .isEqualTo("Die KI-Antwort entsprach nicht den erwarteten Planungsregeln.");
                assertThat(codes.get(unavailableGeneration)).isEqualTo("PROVIDER_UNAVAILABLE");
                assertThat(operations.get(unavailableGeneration)).isEqualTo("PLAN_GENERATION");
                assertThat(retryable.get(unavailableGeneration)).isTrue();
                assertThat(diagnoses.get(unavailableGeneration))
                        .isEqualTo("Der KI-Anbieter war vorübergehend nicht erreichbar.");
            }
        }
    }

    @Test
    void migratesLegacyStatusRetryColumnAndCompletionTokenHistory() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:ai-workflow-cleanup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "")) {
            UUID workflowId = UUID.randomUUID();
            UUID completionToken = UUID.randomUUID();
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE ai_plan_generation_workflows (
                            id UUID PRIMARY KEY,
                            created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
                            updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
                            lock_version BIGINT NOT NULL DEFAULT 0,
                            completion_token UUID NOT NULL,
                            status VARCHAR(40) NOT NULL,
                            retry_count INTEGER NOT NULL DEFAULT 0,
                            CONSTRAINT ck_ai_workflows_status CHECK (status IN (
                                'PRE_CHECK_PASSED', 'GENERATION_PENDING')),
                            CONSTRAINT ck_ai_workflows_retry_count CHECK (retry_count >= 0)
                        )
                        """);
                statement.execute("""
                        INSERT INTO ai_plan_generation_workflows (
                            id, created_at, updated_at, completion_token, status, retry_count
                        ) VALUES ('%s', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '%s', 'PRE_CHECK_PASSED', 2)
                        """.formatted(workflowId, completionToken));
            }

            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/V9__clean_ai_workflow_state.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/V10__preserve_ai_completion_tokens.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/V11__add_controlled_generation_retries.sql"));

            try (var statement = connection.createStatement();
                 var workflow = statement.executeQuery("""
                         SELECT status, pre_check_retry_count, generation_prompt_version,
                                generation_round_attempt_count, generation_total_attempt_count
                         FROM ai_plan_generation_workflows
                         WHERE id = '%s'
                         """.formatted(workflowId))) {
                assertThat(workflow.next()).isTrue();
                assertThat(workflow.getString("status")).isEqualTo("GENERATION_PENDING");
                assertThat(workflow.getInt("pre_check_retry_count")).isEqualTo(2);
                assertThat(workflow.getString("generation_prompt_version")).isEqualTo("generation-v1");
                assertThat(workflow.getInt("generation_round_attempt_count")).isZero();
                assertThat(workflow.getInt("generation_total_attempt_count")).isZero();
            }
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        INSERT INTO ai_workflow_acknowledged_warnings (workflow_id, problem_index)
                        VALUES ('%s', 0)
                        """.formatted(workflowId));
                assertThatThrownBy(() -> statement.execute("""
                        INSERT INTO ai_workflow_acknowledged_warnings (workflow_id, problem_index)
                        VALUES ('%s', 0)
                        """.formatted(workflowId))).isInstanceOf(SQLException.class);
            }
            try (var statement = connection.createStatement();
                 var token = statement.executeQuery("""
                         SELECT workflow_id FROM ai_workflow_completion_tokens
                         WHERE completion_token = '%s'
                         """.formatted(completionToken))) {
                assertThat(token.next()).isTrue();
                assertThat(token.getObject("workflow_id", UUID.class)).isEqualTo(workflowId);
            }
        }
    }

    @Test
    void createsJsonWorkflowStorageWithUniqueProjectAndCompletionToken() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:ai-workflow-migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "")) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE projects (id UUID PRIMARY KEY)");
            }

            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/V4__add_ai_plan_generation_workflows.sql"));

            UUID projectId = UUID.randomUUID();
            UUID workflowId = UUID.randomUUID();
            UUID completionToken = UUID.randomUUID();
            try (var statement = connection.createStatement()) {
                statement.execute("INSERT INTO projects (id) VALUES ('" + projectId + "')");
                statement.execute("""
                        INSERT INTO ai_plan_generation_workflows (
                            id, created_at, updated_at, project_id, confirmed_snapshot,
                            snapshot_version, completion_token, status, retry_count
                        ) VALUES (
                            '%s', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '%s',
                            '{"title":"Test"}', 'ai-wizard-v1', '%s', 'PRE_CHECK_PENDING', 0
                        )
                        """.formatted(workflowId, projectId, completionToken));

                ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                        "db/migration/V5__add_ai_workflow_consent_audit.sql"));
                ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                        "db/migration/V6__add_ai_generation_result.sql"));
                statement.execute("""
                        UPDATE ai_plan_generation_workflows
                        SET status = 'GENERATION_COMPLETED',
                            generated_plan = '{"schemaVersion":"1.0"}'
                        WHERE id = '%s'
                        """.formatted(workflowId));
                try (var result = statement.executeQuery("""
                        SELECT confirmed_snapshot, snapshot_version, status, retry_count,
                               consent_confirmed_at, consent_version, generated_plan
                        FROM ai_plan_generation_workflows
                        WHERE id = '%s'
                        """.formatted(workflowId))) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString("confirmed_snapshot")).contains("Test");
                    assertThat(result.getString("snapshot_version")).isEqualTo("ai-wizard-v1");
                    assertThat(result.getString("status")).isEqualTo("GENERATION_COMPLETED");
                    assertThat(result.getInt("retry_count")).isZero();
                    assertThat(result.getTimestamp("consent_confirmed_at")).isNotNull();
                    assertThat(result.getString("consent_version")).isEqualTo("v1");
                    assertThat(result.getString("generated_plan")).contains("schemaVersion");
                }

                assertThatThrownBy(() -> statement.execute("""
                        INSERT INTO ai_plan_generation_workflows (
                            id, created_at, updated_at, project_id, confirmed_snapshot,
                            snapshot_version, completion_token, status, retry_count,
                            consent_confirmed_at, consent_version
                        ) VALUES (
                            RANDOM_UUID(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '%s',
                            '{}', 'ai-wizard-v1', RANDOM_UUID(), 'PRE_CHECK_PENDING', 0,
                            CURRENT_TIMESTAMP, 'v1'
                        )
                        """.formatted(projectId)))
                        .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> statement.execute("""
                        UPDATE ai_plan_generation_workflows
                        SET retry_count = -1
                        WHERE id = '%s'
                        """.formatted(workflowId)))
                        .isInstanceOf(SQLException.class);
            }
        }
    }
}
