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

            try (var statement = connection.createStatement();
                 var workflow = statement.executeQuery("""
                         SELECT status, pre_check_retry_count
                         FROM ai_plan_generation_workflows
                         WHERE id = '%s'
                         """.formatted(workflowId))) {
                assertThat(workflow.next()).isTrue();
                assertThat(workflow.getString("status")).isEqualTo("GENERATION_PENDING");
                assertThat(workflow.getInt("pre_check_retry_count")).isEqualTo(2);
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
