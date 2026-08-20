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
                try (var result = statement.executeQuery("""
                        SELECT confirmed_snapshot, snapshot_version, status, retry_count,
                               consent_confirmed_at, consent_version
                        FROM ai_plan_generation_workflows
                        WHERE id = '%s'
                        """.formatted(workflowId))) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString("confirmed_snapshot")).contains("Test");
                    assertThat(result.getString("snapshot_version")).isEqualTo("ai-wizard-v1");
                    assertThat(result.getString("status")).isEqualTo("PRE_CHECK_PENDING");
                    assertThat(result.getInt("retry_count")).isZero();
                    assertThat(result.getTimestamp("consent_confirmed_at")).isNotNull();
                    assertThat(result.getString("consent_version")).isEqualTo("v1");
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
