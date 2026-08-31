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
    void hardensAiRunsWithExpiryAndOneActiveRunPerProject() throws Exception {
        String schema = "ai_run_test_" + java.util.UUID.randomUUID().toString().replace("-", "");
        var dataSource = java.util.Objects.requireNonNull(jdbc.getDataSource());
        jdbc.execute("CREATE SCHEMA " + schema);
        try (var connection = dataSource.getConnection()) {
            String originalSchema = connection.getSchema();
            try {
                org.flywaydb.core.Flyway.configure().dataSource(dataSource)
                        .schemas(schema).defaultSchema(schema).load().migrate();
                connection.setSchema(schema);
                var scoped = new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true));

                assertThat(scoped.queryForList("""
                        SELECT column_name FROM information_schema.columns
                        WHERE table_schema = ? AND table_name = 'ai_plan_generation_workflows'
                          AND column_name IN ('active_run_id', 'run_expires_at')
                        ORDER BY column_name
                        """, String.class, schema))
                        .containsExactly("active_run_id", "run_expires_at");
                assertThat(scoped.queryForObject("""
                        SELECT indexdef FROM pg_indexes
                        WHERE schemaname = ? AND indexname = 'uk_ai_workflows_active_project'
                        """, String.class, schema))
                        .contains("UNIQUE INDEX")
                        .contains("WHERE")
                        .contains("GENERATION_RUNNING")
                        .contains("PRE_CHECK_RUNNING");
                assertThat(scoped.queryForObject("""
                        SELECT pg_get_constraintdef(c.oid)
                        FROM pg_constraint c
                        JOIN pg_namespace n ON n.oid = c.connamespace
                        WHERE n.nspname = ? AND c.conname = 'ck_ai_workflows_active_run'
                        """, String.class, schema))
                        .contains("active_run_id IS NOT NULL")
                        .contains("run_expires_at IS NOT NULL");
            } finally {
                connection.setSchema(originalSchema);
            }
        } finally {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void removesOnlySectionDateColumnsAndPreservesExistingSectionData() throws Exception {
        String schema = "section_test_" + java.util.UUID.randomUUID().toString().replace("-", "");
        var dataSource = java.util.Objects.requireNonNull(jdbc.getDataSource());
        jdbc.execute("CREATE SCHEMA " + schema);
        try (var connection = dataSource.getConnection()) {
            String originalSchema = connection.getSchema();
            try {
                org.flywaydb.core.Flyway.configure().dataSource(dataSource)
                        .schemas(schema).defaultSchema(schema).target("18").load().migrate();
                connection.setSchema(schema);
                var scoped = new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true));
                var projectId = java.util.UUID.randomUUID();
                var activeSectionId = java.util.UUID.randomUUID();
                var draftId = java.util.UUID.randomUUID();
                var draftSectionId = java.util.UUID.randomUUID();
                scoped.update("INSERT INTO plan_containers (id, created_at, updated_at, title) VALUES (?, now(), now(), 'Migration')", projectId);
                scoped.update("INSERT INTO projects (id, creation_type, status, location) VALUES (?, 'AI', 'DRAFT', 'DRAFT')", projectId);
                scoped.update("""
                        INSERT INTO plan_sections
                        (id, created_at, updated_at, plan_container_id, title, description, start_date,
                         end_date, relative_start_day, relative_end_day, sort_order, origin)
                        VALUES (?, now(), now(), ?, 'Inhaltlicher Bereich', 'Bleibt erhalten',
                                DATE '2026-09-01', DATE '2026-09-10', 0, 9, 3, 'USER')
                        """, activeSectionId, projectId);
                scoped.update("INSERT INTO plan_drafts (id, created_at, updated_at, project_id, status) VALUES (?, now(), now(), ?, 'READY_FOR_REVIEW')",
                        draftId, projectId);
                scoped.update("""
                        INSERT INTO draft_sections
                        (id, created_at, updated_at, plan_draft_id, title, description, start_date, end_date, sort_order)
                        VALUES (?, now(), now(), ?, 'KI-Bereich', 'Entwurfsinhalt', DATE '2026-09-02', DATE '2026-09-08', 4)
                        """, draftSectionId, draftId);

                org.flywaydb.core.Flyway.configure().dataSource(dataSource)
                        .schemas(schema).defaultSchema(schema).load().migrate();

                assertThat(scoped.queryForMap("SELECT title, description, sort_order FROM plan_sections WHERE id = ?", activeSectionId))
                        .containsEntry("title", "Inhaltlicher Bereich")
                        .containsEntry("description", "Bleibt erhalten")
                        .containsEntry("sort_order", 3);
                assertThat(scoped.queryForMap("SELECT title, description, sort_order FROM draft_sections WHERE id = ?", draftSectionId))
                        .containsEntry("title", "KI-Bereich")
                        .containsEntry("description", "Entwurfsinhalt")
                        .containsEntry("sort_order", 4);
                assertThat(scoped.queryForList("""
                        SELECT column_name FROM information_schema.columns
                        WHERE table_schema = ? AND table_name IN ('plan_sections', 'draft_sections')
                          AND column_name IN ('start_date', 'end_date', 'relative_start_day', 'relative_end_day')
                        """, String.class, schema)).isEmpty();
            } finally {
                connection.setSchema(originalSchema);
            }
        } finally {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }


    @Test
    void migratesTypedSubcategoriesAndSnapshotsWithoutGuessingUnknownValues() throws Exception {
        // Separate schema inside the explicitly supplied disposable database.
        String schema = "subcategory_test_" + java.util.UUID.randomUUID().toString().replace("-", "");
        var dataSource = java.util.Objects.requireNonNull(jdbc.getDataSource());
        jdbc.execute("CREATE SCHEMA " + schema);
        try (var connection = dataSource.getConnection()) {
            String originalSchema = connection.getSchema();
            try {
                org.flywaydb.core.Flyway.configure().dataSource(dataSource)
                        .schemas(schema).defaultSchema(schema).target("16").load().migrate();
                connection.setSchema(schema);
                var scoped = new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true));
                var cases = new java.util.ArrayList<LegacyCase>();
                for (var value : de.melinadanhier.projectflow.plancontainer.project.model.ProjectSubCategory.values()) {
                    cases.add(seedClassification(scoped, value.getCategory().name(), value.getLabel(), value.name(), false));
                    cases.add(seedClassification(scoped, value.getCategory().name(), " " + value.name().toLowerCase(java.util.Locale.ROOT) + " ", value.name(), true));
                }
                cases.add(seedClassification(scoped, "EDUCATION", "Bachelorarbeit", "THESIS", false));
                cases.add(seedClassification(scoped, "EDUCATION", "Präsentation", "PRESENTATION_OR_REPORT", true));
                cases.add(seedClassification(scoped, "EDUCATION", "Gruppenpräsentation", "PRESENTATION_OR_REPORT", false));
                cases.add(seedClassification(scoped, "OTHER", "Mein besonderes Vorhaben", null, false));
                cases.add(seedClassification(scoped, "HOME", "   ", null, false));
                cases.add(seedClassification(scoped, "EDUCATION", "unbekannt & individuell", null, true));
                cases.add(seedClassification(scoped, "EDUCATION", "MOVING", null, false));
                org.flywaydb.core.Flyway.configure().dataSource(dataSource)
                        .schemas(schema).defaultSchema(schema).load().migrate();

                var codec = new de.melinadanhier.projectflow.generation.persistence.AiWorkflowPayloadCodec(
                        tools.jackson.databind.json.JsonMapper.builder().build());
                for (var testCase : cases) {
                    for (String table : java.util.List.of("projects", "plan_templates")) {
                        var id = table.equals("projects") ? testCase.projectId() : testCase.templateId();
                        assertThat(scoped.queryForObject("SELECT subcategory FROM " + table + " WHERE id = ?",
                                String.class, id)).isEqualTo(testCase.expected());
                        assertThat(scoped.queryForObject("SELECT other_project_type_description FROM " + table + " WHERE id = ?",
                                String.class, id)).isEqualTo(testCase.category().equals("OTHER") ? testCase.legacy() : null);
                    }
                    String json = scoped.queryForObject(
                            "SELECT confirmed_snapshot::text FROM ai_plan_generation_workflows WHERE id = ?",
                            String.class, testCase.workflowId());
                    var snapshot = codec.readSnapshot(json);
                    assertThat(snapshot.subcategory() == null ? null : snapshot.subcategory().name()).isEqualTo(testCase.expected());
                    assertThat(snapshot.otherProjectTypeDescription()).isEqualTo(
                            testCase.category().equals("OTHER") ? testCase.legacy() : null);
                    assertThat(json).doesNotContain("\"projectType\"");
                }
                assertThat(scoped.queryForList("SELECT legacy_value FROM project_subcategory_migration_issues", String.class))
                        .containsExactlyInAnyOrder("unbekannt & individuell", "unbekannt & individuell", "unbekannt & individuell",
                                "MOVING", "MOVING", "MOVING");
                assertThat(scoped.queryForObject(
                        "SELECT count(*) FROM ai_plan_generation_workflows WHERE snapshot_version <> 'ai-wizard-v3'",
                        Integer.class)).isZero();
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> scoped.update(
                        "UPDATE projects SET subcategory = 'MOVING' WHERE category = 'EDUCATION'"))
                        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> scoped.update(
                        "UPDATE plan_templates SET subcategory = 'THESIS' WHERE category = 'OTHER'"))
                        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> scoped.update(
                        "UPDATE projects SET subcategory = 'UNKNOWN' WHERE category = 'HOME'"))
                        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            } finally {
                connection.setSchema(originalSchema);
            }
        } finally {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private LegacyCase seedClassification(JdbcTemplate scoped, String category, String legacy,
                                           String expected, boolean wrappedJson) {
        var projectId = java.util.UUID.randomUUID();
        var templateId = java.util.UUID.randomUUID();
        var workflowId = java.util.UUID.randomUUID();
        for (var id : java.util.List.of(projectId, templateId)) {
            scoped.update("INSERT INTO plan_containers (id, created_at, updated_at, title) VALUES (?, now(), now(), 'Migrationstest')", id);
        }
        scoped.update("INSERT INTO projects (id, creation_type, status, category, project_type) VALUES (?, 'EMPTY', 'ACTIVE', ?, ?)",
                projectId, category, legacy);
        scoped.update("INSERT INTO plan_templates (id, category, project_type, collaboration_mode) VALUES (?, ?, ?, 'INDIVIDUAL')",
                templateId, category, legacy);
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        String json = mapper.writeValueAsString(java.util.Map.of(
                "title", "Migrationstest", "category", category, "projectType", legacy, "collaborationMode", "INDIVIDUAL"));
        scoped.update("""
                INSERT INTO ai_plan_generation_workflows
                (id, created_at, updated_at, project_id, confirmed_snapshot, snapshot_version, completion_token,
                 consent_confirmed_at, consent_version)
                VALUES (?, now(), now(), ?, CAST(? AS jsonb), 'ai-wizard-v2', ?, now(), 'v1')
                """, workflowId, projectId, wrappedJson ? mapper.writeValueAsString(json) : json, java.util.UUID.randomUUID());
        return new LegacyCase(projectId, templateId, workflowId, category, legacy, expected);
    }

    private record LegacyCase(java.util.UUID projectId, java.util.UUID templateId, java.util.UUID workflowId,
                              String category, String legacy, String expected) {}

    @Test
    void allMigrationsRemoveElementAssumptionsAndAddWorkflowContext() {
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = 'public' and table_name = 'draft_plan_elements'
                and column_name = 'critical_assumption'
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = 'public' and table_name = 'ai_plan_generation_workflows'
                and column_name in ('generation_assumption_context', 'pending_assumption_review')
                """, Integer.class)).isEqualTo(2);
        String statusConstraint = jdbc.queryForObject("""
                select pg_get_constraintdef(oid)
                from pg_constraint
                where conname = 'ck_ai_workflows_status'
                """, String.class);
        assertThat(statusConstraint)
                .contains("ASSUMPTIONS_REVIEW_PENDING")
                .doesNotContain("DRAFT_APPLIED")
                .doesNotContain("PRE_CHECK_PASSED");
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = 'public' and table_name = 'plan_drafts'
                and column_name = 'applied_at'
                """, Integer.class)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success = false", Integer.class)).isZero();
    }
}
