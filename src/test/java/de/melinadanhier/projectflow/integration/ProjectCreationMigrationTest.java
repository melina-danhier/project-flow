package de.melinadanhier.projectflow.integration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectCreationMigrationTest {

    @Test
    void migratesManualAndExistingDraftRowsBeforeAddingTheNewConstraints() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:project-creation-migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "")) {
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE projects (
                            id UUID PRIMARY KEY,
                            creation_type VARCHAR(20) NOT NULL,
                            status VARCHAR(20) NOT NULL,
                            location VARCHAR(20) NOT NULL
                        )
                        """);
                statement.execute("""
                        ALTER TABLE projects ADD CONSTRAINT ck_projects_creation_type
                        CHECK (creation_type IN ('MANUAL', 'TEMPLATE', 'AI'))
                        """);
                statement.execute("""
                        ALTER TABLE projects ADD CONSTRAINT ck_projects_location
                        CHECK (location IN ('OVERVIEW', 'TRASH', 'ARCHIVE'))
                        """);
                statement.execute("CREATE TABLE plan_templates (category VARCHAR(50) NOT NULL)");
                statement.execute("""
                        ALTER TABLE plan_templates ADD CONSTRAINT ck_plan_templates_category
                        CHECK (category IN ('EDUCATION'))
                        """);
                statement.execute("""
                        INSERT INTO projects (id, creation_type, status, location)
                        VALUES
                          (RANDOM_UUID(), 'MANUAL', 'ACTIVE', 'OVERVIEW'),
                          (RANDOM_UUID(), 'AI', 'DRAFT', 'OVERVIEW')
                        """);
            }

            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/V3__prepare_project_creation_flows_and_drafts.sql"));

            try (var statement = connection.createStatement()) {
                try (var result = statement.executeQuery(
                        "SELECT creation_type FROM projects WHERE status = 'ACTIVE'")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isEqualTo("EMPTY");
                }
                try (var result = statement.executeQuery(
                        "SELECT location FROM projects WHERE status = 'DRAFT'")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isEqualTo("DRAFT");
                }

                assertThatThrownBy(() -> statement.execute(
                        "UPDATE projects SET location = 'OVERVIEW' WHERE status = 'DRAFT'"))
                        .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> statement.execute(
                        "UPDATE projects SET creation_type = 'MANUAL' WHERE status = 'ACTIVE'"))
                        .isInstanceOf(SQLException.class);
            }
        }
    }
}
