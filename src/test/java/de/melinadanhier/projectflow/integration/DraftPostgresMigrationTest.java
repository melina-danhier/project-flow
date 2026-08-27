package de.melinadanhier.projectflow.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** Run only against a disposable database supplied explicitly on the Maven command line. */
@EnabledIfSystemProperty(named = "projectflow.test.postgres.url", matches = ".+")
@SpringBootTest(properties = {
        "spring.datasource.url=${projectflow.test.postgres.url}",
        "spring.datasource.username=${projectflow.test.postgres.username:draft_review_test}",
        "spring.datasource.password=${projectflow.test.postgres.password:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
class DraftPostgresMigrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void allMigrationsAndHibernateValidationPreserveNullableDraftAssumptions() {
        assertThat(jdbc.queryForObject("""
                select character_maximum_length from information_schema.columns
                where table_schema = 'public' and table_name = 'draft_plan_elements'
                and column_name = 'critical_assumption'
                """, Integer.class)).isEqualTo(2000);
        assertThat(jdbc.queryForObject("""
                select is_nullable from information_schema.columns
                where table_schema = 'public' and table_name = 'draft_plan_elements'
                and column_name = 'critical_assumption'
                """, String.class)).isEqualTo("YES");
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success = false", Integer.class)).isZero();
    }
}
