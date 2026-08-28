package de.melinadanhier.projectflow.draft;

import de.melinadanhier.projectflow.ai.provider.AiClient;
import de.melinadanhier.projectflow.ai.model.generation.*;
import de.melinadanhier.projectflow.draft.dto.DraftReviewDto;
import de.melinadanhier.projectflow.draft.model.*;
import de.melinadanhier.projectflow.draft.repository.PlanDraftRepository;
import de.melinadanhier.projectflow.draft.service.*;
import de.melinadanhier.projectflow.plancontainer.project.model.*;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import de.melinadanhier.projectflow.user.model.User;
import de.melinadanhier.projectflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DraftReviewIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ProjectRepository projects;
    @Autowired UserRepository users;
    @Autowired PlanDraftRepository drafts;
    @Autowired de.melinadanhier.projectflow.draft.mapper.GeneratedPlanDraftMapper generatedPlanMapper;
    @Autowired DraftReviewService reviews;
    @Autowired DraftApplicationService application;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AiClient aiClient;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t\n", "\u2003"})
    void absentAssumptionsHaveNoMarkerAndApplyDirectly(String assumption) throws Exception {
        Fixture f = fixture(assumption, null);
        assertThat(review(f).getElements()).allSatisfy(element -> {
            assertThat(element.getCriticalAssumption()).isNull();
            assertThat(element.isHasCriticalAssumption()).isFalse();
        });
        mvc.perform(get(f.url()).with(user(f.owner())))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("class=\"assumption-toggle\""))));
        mvc.perform(post(f.url() + "/apply").with(user(f.owner())).with(csrf()))
                .andExpect(redirectedUrl("/projects/" + f.projectId() + "/plan"));
        assertApplied(f);
        verifyNoInteractions(aiClient);
    }

    @Test
    void markerIsAccessibleAndEscapesUntrustedText() throws Exception {
        Fixture f = fixture("  Material <script>alert(1)</script> ist verfügbar  ", null);
        var task = review(f).getElements().getFirst();
        assertThat(task.getCriticalAssumption()).isEqualTo("Material <script>alert(1)</script> ist verfügbar");
        mvc.perform(get(f.url()).with(user(f.owner())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("aria-label=\"Kritische Annahme anzeigen\"")))
                .andExpect(content().string(containsString("aria-describedby=\"assumption-" + task.getId())))
                .andExpect(content().string(containsString("role=\"tooltip\"")))
                .andExpect(content().string(containsString("<span aria-hidden=\"true\">!</span>")))
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)"))))
                .andDo(result -> writePreview("draft-review.html", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void legacyWhitespaceDoesNotCreateAMarkerOrRequireConfirmation() throws Exception {
        Fixture f = fixture("Material ist verfügbar", null);
        var taskId = review(f).getElements().getFirst().getId();
        jdbc.update("update draft_plan_elements set critical_assumption = '   ', has_critical_assumption = true where id = ?", taskId);
        mvc.perform(get(f.url()).with(user(f.owner())))
                .andExpect(content().string(not(containsString("class=\"assumption-toggle\""))));
        application.apply(f.projectId(), f.owner().userId());
        assertApplied(f);
    }

    @Test
    void concurrentConfirmationsCreateOnlyOneActivePlan() throws Exception {
        Fixture f = fixture("Material ist verfügbar", null);
        long version = review(f).getLockVersion();
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<UUID> confirmation = () -> {
                start.await();
                return application.confirmAndApply(f.projectId(), f.owner().userId(), version);
            };
            var first = executor.submit(confirmation);
            var second = executor.submit(confirmation);
            start.countDown();
            assertThat(first.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(f.projectId());
            assertThat(second.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(f.projectId());
        }
        assertApplied(f);
    }

    @Test
    void confirmationListsAllPendingAssumptionsAndBackDoesNotMutate() throws Exception {
        Fixture f = fixture("Material ist verfügbar", "Raum ist frei\n" + "Vollständiger Text. ".repeat(70));
        var before = review(f);
        mvc.perform(post(f.url() + "/apply").with(user(f.owner())).with(csrf()))
                .andExpect(status().isOk()).andExpect(view().name("generation/draft-confirmation"))
                .andExpect(content().string(containsString("Aufgabe 1")))
                .andExpect(content().string(containsString("Aufgabe 2")))
                .andExpect(content().string(containsString(before.getElements().get(1).getCriticalAssumption())))
                .andExpect(content().string(containsString("Kritische Annahmen bestätigen und Plan übernehmen")))
                .andExpect(content().string(containsString("Zurück zum Entwurf")))
                .andDo(result -> writePreview("draft-confirmation.html", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
        mvc.perform(get(f.url()).with(user(f.owner()))).andExpect(status().isOk());
        assertThat(review(f)).usingRecursiveComparison().isEqualTo(before);
        assertEmptyPlan(f);
    }

    @Test
    void reviewedAssumptionDoesNotRequireConfirmationAndFilterOnlyShowsPendingElements() throws Exception {
        Fixture f = fixture("Material ist verfügbar", null);
        var draft = review(f);
        mvc.perform(post(f.url() + "/elements/" + draft.getElements().getFirst().getId() + "/accept")
                        .param("lockVersion", String.valueOf(draft.getLockVersion())).with(user(f.owner())).with(csrf()))
                .andExpect(redirectedUrl(f.url()));
        mvc.perform(get(f.url()).param("pendingOnly", "true").with(user(f.owner())))
                .andExpect(content().string(not(containsString("Aufgabe 1"))))
                .andExpect(content().string(containsString("Aufgabe 2")));
        mvc.perform(post(f.url() + "/apply").with(user(f.owner())).with(csrf()))
                .andExpect(redirectedUrl("/projects/" + f.projectId() + "/plan"));
        assertApplied(f);
    }

    @Test
    void explicitConfirmationAppliesExactlyOnceAndDoesNotCopyWorkflowFields() throws Exception {
        Fixture f = fixture("Material ist verfügbar", "Raum ist frei");
        long version = review(f).getLockVersion();
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post(f.url() + "/confirm-and-apply").param("lockVersion", String.valueOf(version))
                            .param("criticalAssumption", "Manipuliert").with(user(f.owner())).with(csrf()))
                    .andExpect(redirectedUrl("/projects/" + f.projectId() + "/plan"));
        }
        assertApplied(f);
        assertThat(jdbc.queryForList("select critical_assumption from plan_elements where plan_container_id = ?",
                String.class, f.projectId())).containsOnlyNulls();
        assertThat(jdbc.queryForList("select has_critical_assumption from plan_elements where plan_container_id = ?",
                Boolean.class, f.projectId())).containsOnly(false);
        assertThat(jdbc.queryForList("select status from tasks where id in (select id from plan_elements where plan_container_id = ?)",
                String.class, f.projectId())).containsOnly("OPEN");
        assertThat(review(f).getElements()).allSatisfy(element ->
                assertThat(element.getReviewStatus()).isEqualTo(DraftReviewStatus.ACCEPTED));
        verifyNoInteractions(aiClient);
    }

    @Test
    void editPreservesAssumptionIgnoresForgedFieldsAndInvalidatesOldConfirmation() throws Exception {
        Fixture f = fixture("Material ist verfügbar", null);
        var before = review(f);
        var taskId = before.getElements().getFirst().getId();
        reviews.acceptElement(f.projectId(), taskId, f.owner().userId(), before.getLockVersion());
        long version = review(f).getLockVersion();
        mvc.perform(post(f.url() + "/tasks/" + taskId)
                        .param("lockVersion", String.valueOf(version)).param("title", "Überarbeitet")
                        .param("priority", "HIGH").param("criticalAssumption", "Manipuliert")
                        .param("reviewStatus", "ACCEPTED").with(user(f.owner())).with(csrf()))
                .andExpect(redirectedUrl(f.url()));
        var current = review(f);
        assertThat(current.getLockVersion()).isGreaterThan(version);
        assertThat(current.getElements().getFirst().getTitle()).isEqualTo("Überarbeitet");
        assertThat(current.getElements().getFirst().getCriticalAssumption()).isEqualTo("Material ist verfügbar");
        assertThat(current.getElements().getFirst().getReviewStatus()).isEqualTo(DraftReviewStatus.PENDING);
        mvc.perform(post(f.url() + "/confirm-and-apply").param("lockVersion", String.valueOf(version))
                        .with(user(f.owner())).with(csrf()))
                .andExpect(redirectedUrl(f.url())).andExpect(flash().attribute("errorMessage", containsString("zwischenzeitlich")));
        assertEmptyPlan(f);
        verifyNoInteractions(aiClient);
    }

    @Test
    void deletingTaskAlsoRemovesItsAssumption() throws Exception {
        Fixture f = fixture("Material ist verfügbar", null);
        var before = review(f);
        mvc.perform(post(f.url() + "/tasks/" + before.getElements().getFirst().getId() + "/delete")
                        .param("lockVersion", String.valueOf(before.getLockVersion())).with(user(f.owner())).with(csrf()))
                .andExpect(redirectedUrl(f.url()));
        assertThat(review(f).getUncheckedCriticalTasks()).isEmpty();
        assertThat(review(f).getElements()).hasSize(3);
        application.apply(f.projectId(), f.owner().userId());
        assertThat(elementCount(f)).isEqualTo(3);
    }

    @Test
    void currentDraftIsRevalidatedAndFailureLeavesNoPartialPlan() throws Exception {
        Fixture f = fixture("Material ist verfügbar", null);
        var before = review(f);
        mvc.perform(post(f.url() + "/apply").with(user(f.owner())).with(csrf()))
                .andExpect(view().name("generation/draft-confirmation"));
        // Simulate persisted domain-invalid dates, which are legal SQL values.
        jdbc.update("update draft_tasks set start_date = ?, due_date = ? where id = ?",
                LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 1), before.getElements().getFirst().getId());
        mvc.perform(post(f.url() + "/confirm-and-apply").param("lockVersion", String.valueOf(before.getLockVersion()))
                        .with(user(f.owner())).with(csrf()))
                .andExpect(redirectedUrl(f.url())).andExpect(flash().attribute("errorMessage", containsString("Fälligkeitsdatum")));
        assertEmptyPlan(f);
        assertThat(review(f).getStatus()).isEqualTo(DraftPlanStatus.READY_FOR_REVIEW);
        assertThat(review(f).getElements()).allSatisfy(element ->
                assertThat(element.getReviewStatus()).isEqualTo(DraftReviewStatus.PENDING));
        verifyNoInteractions(aiClient);
    }

    @Test
    void invalidDependencyCannotBeApplied() {
        Fixture f = fixture(null, null);
        var taskId = review(f).getElements().getFirst().getId();
        jdbc.update("insert into draft_task_prerequisites (successor_draft_task_id, prerequisite_draft_task_id) values (?, ?)", taskId, taskId);
        assertThatThrownBy(() -> application.apply(f.projectId(), f.owner().userId()))
                .isInstanceOf(de.melinadanhier.projectflow.common.exception.DomainValidationException.class);
        assertEmptyPlan(f);
    }

    @Test
    void ownerChecksAndCsrfProtectEveryMutationAndConfirmation() throws Exception {
        Fixture f = fixture("Material ist verfügbar", null);
        var outsider = fixture(null, null).owner();
        var draft = review(f);
        var task = draft.getElements().getFirst();
        mvc.perform(get(f.url()).with(user(outsider))).andExpect(status().isNotFound());
        mvc.perform(get(f.url())).andExpect(status().is3xxRedirection());
        for (String suffix : List.of("/apply", "/confirm-and-apply", "/elements/" + task.getId() + "/accept",
                "/sections/" + draft.getSections().getFirst().getId() + "/accept", "/tasks/" + task.getId() + "/delete",
                "/tasks/" + task.getId())) {
            mvc.perform(post(f.url() + suffix).param("lockVersion", "0").param("title", "Titel").param("priority", "LOW")
                            .with(user(outsider)).with(csrf())).andExpect(status().isNotFound());
            mvc.perform(post(f.url() + suffix).param("lockVersion", "0").with(user(f.owner())))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get(f.url() + "/confirm-and-apply").with(user(f.owner())))
                .andExpect(status().isMethodNotAllowed());
        assertEmptyPlan(f);
    }

    private Fixture fixture(String firstAssumption, String secondAssumption) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            User owner = new User();
            owner.setEmail(UUID.randomUUID() + "@example.org");
            owner.setDisplayName("Draft Review Test");
            owner.setPasswordHash("test-hash");
            owner.setEnabled(true);
            users.saveAndFlush(owner);
            Project project = new Project();
            project.setTitle("Testprojekt");
            project.setCreationType(CreationType.AI);
            project.setStatus(ProjectStatus.DRAFT);
            project.setLocation(ProjectLocation.DRAFT);
            ProjectMember membership = new ProjectMember();
            membership.setUser(owner);
            membership.setRole(ProjectMemberRole.OWNER);
            membership.setActive(true);
            project.addMembership(membership);
            projects.saveAndFlush(project);
            List<GeneratedTask> tasks = IntStream.rangeClosed(1, 4).mapToObj(index -> new GeneratedTask(
                    "task-" + index, "Aufgabe " + index, null, null, null, null,
                    index == 1 ? firstAssumption : index == 2 ? secondAssumption : null,
                    GeneratedElementOrigin.AI_INFERRED, index)).toList();
            var contents = generatedPlanMapper.map(new GeneratedPlanResponse(List.of(new GeneratedPhase(
                    "phase", "Phase", null, null, null, 1, tasks, List.of()))));
            DraftPlan draft = new DraftPlan();
            project.attachDraft(draft);
            draft.setStatus(DraftPlanStatus.READY_FOR_REVIEW);
            contents.sections().forEach(draft::addSection);
            contents.elements().forEach(draft::addElement);
            drafts.saveAndFlush(draft);
            return new Fixture(project.getId(), new AuthenticatedUser(owner.getId(), owner.getEmail(), owner.getPasswordHash(), true));
        });
    }

    private DraftReviewDto review(Fixture f) { return reviews.review(f.projectId(), f.owner().userId()); }
    private void writePreview(String name, String html) throws java.io.IOException {
        if (Boolean.getBoolean("projectflow.test.export-draft-html")) {
            var directory = java.nio.file.Path.of("target", "draft-ui-check");
            java.nio.file.Files.createDirectories(directory);
            java.nio.file.Files.writeString(directory.resolve(name), html);
        }
    }
    private long elementCount(Fixture f) {
        return jdbc.queryForObject("select count(*) from plan_elements where plan_container_id = ?", Long.class, f.projectId());
    }
    private void assertEmptyPlan(Fixture f) {
        assertThat(elementCount(f)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from plan_sections where plan_container_id = ?", Long.class, f.projectId())).isZero();
        assertThat(projects.findById(f.projectId()).orElseThrow().getStatus()).isEqualTo(ProjectStatus.DRAFT);
    }
    private void assertApplied(Fixture f) {
        assertThat(elementCount(f)).isEqualTo(4);
        assertThat(review(f).getStatus()).isEqualTo(DraftPlanStatus.APPLIED);
        assertThat(projects.findById(f.projectId()).orElseThrow().getStatus()).isEqualTo(ProjectStatus.ACTIVE);
    }
    private record Fixture(UUID projectId, AuthenticatedUser owner) {
        String url() { return "/projects/" + projectId + "/draft"; }
    }
}
