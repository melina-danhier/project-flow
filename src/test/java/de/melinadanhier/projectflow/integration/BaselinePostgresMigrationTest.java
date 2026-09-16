package de.melinadanhier.projectflow.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** Optional baseline test for an explicitly supplied, empty, disposable PostgreSQL database. */
@EnabledIfSystemProperty(named = "projectflow.test.postgres.url", matches = ".+")
@SpringBootTest(properties = {
        "spring.datasource.url=${projectflow.test.postgres.url}",
        "spring.datasource.username=${projectflow.test.postgres.username:projectflow_migration_test}",
        "spring.datasource.password=${PROJECTFLOW_TEST_POSTGRES_PASSWORD:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
class BaselinePostgresMigrationTest {

    private static final int EXPECTED_APPLICATION_TABLES = 26;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void appliesTheCompleteBaselineAndValidatesTheJpaSchema() {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE type = 'SQL' AND success
                """, Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("""
                SELECT version
                FROM flyway_schema_history
                WHERE success AND version IS NOT NULL
                ORDER BY installed_rank DESC
                LIMIT 1
                """, String.class)).isEqualTo("5");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """, Integer.class)).isEqualTo(EXPECTED_APPLICATION_TABLES);
    }

    @Test
    void seedsTheStaticProjectTemplate() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plan_templates", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM plan_elements
                WHERE plan_container_id = '8d000000-0000-4000-8000-000000000001'
                """, Integer.class)).isEqualTo(26);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM tasks
                WHERE id IN (
                    SELECT id FROM plan_elements
                    WHERE plan_container_id = '8d000000-0000-4000-8000-000000000001'
                )
                """, Integer.class)).isEqualTo(22);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM milestones
                WHERE id IN (
                    SELECT id FROM plan_elements
                    WHERE plan_container_id = '8d000000-0000-4000-8000-000000000001'
                )
                """, Integer.class)).isEqualTo(4);
    }
}
