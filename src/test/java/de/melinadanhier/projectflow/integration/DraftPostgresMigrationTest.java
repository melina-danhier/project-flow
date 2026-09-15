package de.melinadanhier.projectflow.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** Optional smoke test for an explicitly supplied, disposable PostgreSQL database. */
@EnabledIfSystemProperty(named = "projectflow.test.postgres.url", matches = ".+")
@SpringBootTest(properties = {
        "spring.datasource.url=${projectflow.test.postgres.url}",
        "spring.datasource.username=${projectflow.test.postgres.username:draft_review_test}",
        "spring.datasource.password=${PROJECTFLOW_TEST_POSTGRES_PASSWORD:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
class DraftPostgresMigrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void allMigrationsRunSuccessfullyAndCreateTheCoreSchema() {
        assertThat(jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = false", Integer.class))
                .isZero();
        assertThat(jdbc.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = 'public'
                  and table_name in ('app_users', 'projects', 'plan_drafts', 'ai_plan_generation_workflows')
                """, String.class))
                .containsExactlyInAnyOrder(
                        "app_users", "projects", "plan_drafts", "ai_plan_generation_workflows");
    }
}
