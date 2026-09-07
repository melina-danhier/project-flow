package de.melinadanhier.projectflow.integration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class StaticProjectTemplateMigrationTest {

    @Test
    void insertsTheCompleteSmallEventTemplateWithStableOrderedData() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:static-project-template;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "")) {
            createCurrentTemplateTables(connection);

            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/V34__add_small_event_project_template.sql"));

            try (var statement = connection.createStatement()) {
                try (var result = statement.executeQuery("""
                        SELECT pc.title, pc.description, pc.structure_mode, pc.sort_mode,
                               pt.category, pt.subcategory, pt.collaboration_mode
                        FROM plan_containers pc
                        JOIN plan_templates pt ON pt.id = pc.id
                        WHERE pc.id = '8d000000-0000-4000-8000-000000000001'
                        """)) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString("title")).isEqualTo("Kleine Veranstaltung planen");
                    assertThat(result.getString("description")).isEqualTo(
                            "Vorlage zur Planung einer kleineren privaten, studentischen oder ehrenamtlichen Veranstaltung.");
                    assertThat(result.getString("structure_mode")).isEqualTo("THEMATIC");
                    assertThat(result.getString("sort_mode")).isEqualTo("MANUAL");
                    assertThat(result.getString("category")).isEqualTo("EVENT");
                    assertThat(result.getString("subcategory")).isEqualTo("OTHER_EVENT");
                    assertThat(result.getString("collaboration_mode")).isEqualTo("BOTH");
                    assertThat(result.next()).isFalse();
                }

                assertThat(count(statement, "SELECT COUNT(*) FROM plan_sections")).isEqualTo(5);
                assertThat(count(statement, "SELECT COUNT(*) FROM tasks")).isEqualTo(22);
                assertThat(count(statement, "SELECT COUNT(*) FROM milestones")).isEqualTo(4);
                assertThat(count(statement, """
                        SELECT COUNT(*) FROM tasks
                        WHERE start_date IS NOT NULL OR due_date IS NOT NULL
                           OR relative_start_day IS NOT NULL OR relative_due_day IS NOT NULL
                        """)).isZero();
                assertThat(count(statement, """
                        SELECT COUNT(*) FROM milestones
                        WHERE due_date IS NOT NULL OR relative_due_day IS NOT NULL
                        """)).isZero();

                try (var result = statement.executeQuery(
                        "SELECT title, sort_order FROM plan_sections ORDER BY sort_order")) {
                    assertThat(rows(result)).containsExactly(
                            "Grundlagen festlegen|100",
                            "Organisation vorbereiten|200",
                            "Vorbereitung abschließen|300",
                            "Veranstaltung durchführen|400",
                            "Nachbereitung|500"
                    );
                }
                try (var result = statement.executeQuery("""
                        SELECT pe.title, pe.sort_order
                        FROM plan_elements pe
                        WHERE pe.plan_section_id = '8d000000-0000-4000-8000-000000000101'
                        ORDER BY pe.sort_order
                        """)) {
                    assertThat(rows(result)).containsExactly(
                            "Ziel und Art der Veranstaltung festlegen|100",
                            "Teilnehmerkreis festlegen|200",
                            "Budgetrahmen bestimmen|300",
                            "Termin festlegen|400",
                            "Veranstaltungsort auswählen|500",
                            "Rahmenbedingungen festgelegt|600"
                    );
                }
            }
        }
    }

    private void createCurrentTemplateTables(java.sql.Connection connection) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE plan_containers (
                        id UUID PRIMARY KEY, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL, lock_version BIGINT NOT NULL,
                        title VARCHAR(100) NOT NULL, description VARCHAR(2000),
                        structure_mode VARCHAR(20) NOT NULL, sort_mode VARCHAR(20) NOT NULL)
                    """);
            statement.execute("""
                    CREATE TABLE plan_templates (
                        id UUID PRIMARY KEY, category VARCHAR(50) NOT NULL,
                        other_project_type_description VARCHAR(100), subcategory VARCHAR(100),
                        recommended_duration_days INTEGER, collaboration_mode VARCHAR(20) NOT NULL,
                        active BOOLEAN NOT NULL, template_version INTEGER NOT NULL)
                    """);
            statement.execute("""
                    CREATE TABLE plan_sections (
                        id UUID PRIMARY KEY, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL, lock_version BIGINT NOT NULL,
                        plan_container_id UUID NOT NULL, title VARCHAR(100) NOT NULL,
                        description VARCHAR(2000), sort_order INTEGER NOT NULL,
                        origin VARCHAR(20) NOT NULL, review_status VARCHAR(20) NOT NULL)
                    """);
            statement.execute("""
                    CREATE TABLE plan_elements (
                        id UUID PRIMARY KEY, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL, lock_version BIGINT NOT NULL,
                        plan_container_id UUID NOT NULL, plan_section_id UUID,
                        title VARCHAR(100) NOT NULL, description VARCHAR(2000), sort_order INTEGER NOT NULL,
                        origin VARCHAR(20) NOT NULL, review_status VARCHAR(20) NOT NULL)
                    """);
            statement.execute("""
                    CREATE TABLE tasks (
                        id UUID PRIMARY KEY, status VARCHAR(20) NOT NULL, priority VARCHAR(20) NOT NULL,
                        start_date DATE, due_date DATE, relative_start_day INTEGER, relative_due_day INTEGER,
                        assignee_id UUID, completed_at TIMESTAMP WITH TIME ZONE, estimated_hours INTEGER)
                    """);
            statement.execute("""
                    CREATE TABLE milestones (
                        id UUID PRIMARY KEY, due_date DATE, relative_due_day INTEGER,
                        completed BOOLEAN NOT NULL)
                    """);
        }
    }

    private long count(java.sql.Statement statement, String sql) throws Exception {
        try (var result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private java.util.List<String> rows(java.sql.ResultSet result) throws Exception {
        java.util.List<String> rows = new java.util.ArrayList<>();
        while (result.next()) {
            rows.add(result.getString(1) + "|" + result.getInt(2));
        }
        return rows;
    }
}
