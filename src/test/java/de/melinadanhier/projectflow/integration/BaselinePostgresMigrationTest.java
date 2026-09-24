package de.melinadanhier.projectflow.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/** Baseline integration test for PostgreSQL database migrations using Testcontainers. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
class BaselinePostgresMigrationTest {

    private static final int EXPECTED_APPLICATION_TABLES = 26;
    private static final int EXPECTED_SQL_MIGRATIONS = 10;
    private static final String EXPECTED_LATEST_VERSION = "10";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void appliesTheCompleteBaselineAndValidatesTheJpaSchema() {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE type = 'SQL' AND success
                """, Integer.class)).isEqualTo(EXPECTED_SQL_MIGRATIONS);
        assertThat(jdbc.queryForObject("""
                SELECT version
                FROM flyway_schema_history
                WHERE success AND version IS NOT NULL
                ORDER BY installed_rank DESC
                LIMIT 1
                """, String.class)).isEqualTo(EXPECTED_LATEST_VERSION);
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
