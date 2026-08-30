package de.melinadanhier.projectflow.draft;

import de.melinadanhier.projectflow.ai.provider.AiClient;
import de.melinadanhier.projectflow.ai.model.generation.*;
import de.melinadanhier.projectflow.draft.dto.DraftReviewDto;
import de.melinadanhier.projectflow.draft.model.*;
import de.melinadanhier.projectflow.draft.repository.PlanDraftRepository;
import de.melinadanhier.projectflow.draft.service.*;
import de.melinadanhier.projectflow.plancontainer.project.model.*;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
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
    void absentAssumptionsStillRequireExplicitPendingConfirmation(String assumption) throws Exception {
        Fixture f = fixture(assumption, null);
        mvc.perform(get(f.reviewUrl()).with(user(f.owner())))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("class=\"assumption-toggle\""))));
        mvc.perform(post(f.url() + "/apply").with(user(f.owner())).with(csrf()))
                .andExpect(status().isOk()).andExpect(view().name("generation/draft-pending-confirmation"));
        mvc.perform(post(f.url() + "/continue-with-pending")
                        .param("lockVersion", String.valueOf(review(f).getLockVersion()))
                        .with(user(f.owner())).with(csrf()))
                .andExpect(redirectedUrl("/projects/" + f.projectId() + "/plan"));
        assertApplied(f);
        verifyNoInteractions(aiClient);
    }

    @Test
    void draftReviewContainsNoElementRelatedAssumptionMarkers() throws Exception {
        Fixture f = fixture("  Material <script>alert(1)</script> ist verfügbar  ", null);
        mvc.perform(get(f.reviewUrl()).with(user(f.owner())))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("assumption-toggle"))))
                .andDo(result -> writePreview("draft-review.html", result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
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
                .andExpect(status().isOk()).andExpect(view().name("generation/draft-pending-confirmation"));
        mvc.perform(post(f.url() + "/continue-with-pending")
                        .param("lockVersion", String.valueOf(before.getLockVersion()))
                        .with(user(f.owner())).with(csrf()))
                .andExpect(redirectedUrl("/projects/" + f.projectId() + "/plan"));
        assertApplied(f);
    }

    @Test
    void acceptedElementStillRequiresPendingElementConfirmation() throws Exception {
        Fixture f = fixture("Material ist verfügbar", null);
        var draft = review(f);
        mvc.perform(post(f.url() + "/elements/" + draft.getElements().getFirst().getId() + "/accept")
                        .param("lockVersion", String.valueOf(draft.getLockVersion())).with(user(f.owner())).with(csrf()))
                .andExpect(redirectedUrl(f.reviewUrl()));
        mvc.perform(get(f.reviewUrl()).with(user(f.owner())))
                .andExpect(content().string(containsString("Aufgabe 1")))
                .andExpect(content().string(containsString("Aufgabe 2")));
        mvc.perform(post(f.url() + "/apply").with(user(f.owner())).with(csrf()))
                .andExpect(view().name("generation/draft-pending-confirmation"));
        var current = review(f);
        mvc.perform(post(f.url() + "/confirm-and-apply")
                        .param("lockVersion", String.valueOf(current.getLockVersion()))
                        .param("includePending", "true").with(user(f.owner())).with(csrf()))
                .andExpect(redirectedUrl("/projects/" + f.projectId() + "/plan"));
        assertApplied(f);
    }

    @Test
    void explicitConfirmationAppliesExactlyOnceAndDoesNotCopyWorkflowFields() throws Exception {
        Fixture f = fixture("Material ist verfügbar", "Raum ist frei");
        long version = review(f).getLockVersion();
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post(f.url() + "/confirm-and-apply").param("lockVersion", String.valueOf(version))
                            .param("includePending", "true").with(user(f.owner())).with(csrf()))
                    .andExpect(redirectedUrl("/projects/" + f.projectId() + "/plan"));
        }
        assertApplied(f);
        assertThat(jdbc.queryForList("select status from tasks where id in (select id from plan_elements where plan_container_id = ?)",
                String.class, f.projectId())).containsOnly("OPEN");
        assertThat(review(f).getElements()).allSatisfy(element ->
                assertThat(element.getReviewStatus()).isEqualTo(DraftReviewStatus.PENDING));
        verifyNoInteractions(aiClient);
    }

    @Test
    void editInvalidatesOldConfirmation() throws Exception {
        Fixture f = fixture("Material ist verfügbar", null);
        var before = review(f);
        var taskId = before.getElements().getFirst().getId();
        reviews.acceptElement(f.projectId(), taskId, f.owner().userId(), before.getLockVersion());
        long version = review(f).getLockVersion();
        mvc.perform(post(f.url() + "/tasks/" + taskId)
                        .param("lockVersion", String.valueOf(version)).param("title", "Überarbeitet")
                        .param("priority", "HIGH")
                        .param("reviewStatus", "ACCEPTED").with(user(f.owner())).with(csrf()))
                .andExpect(redirectedUrl(f.reviewUrl()));
        var current = review(f);
        assertThat(current.getLockVersion()).isGreaterThan(version);
        assertThat(current.getElements().getFirst().getTitle()).isEqualTo("Überarbeitet");
        assertThat(current.getElements().getFirst().getOrigin())
                .isEqualTo(de.melinadanhier.projectflow.planelement.model.ElementOrigin.AI_MODIFIED);
        assertThat(current.getElements().getFirst().getReviewStatus()).isEqualTo(DraftReviewStatus.ACCEPTED);
        mvc.perform(post(f.url() + "/confirm-and-apply").param("lockVersion", String.valueOf(version))
                        .with(user(f.owner())).with(csrf()))
                .andExpect(redirectedUrl(f.reviewUrl())).andExpect(flash().attribute("errorMessage", not(blankOrNullString())));
        assertEmptyPlan(f);
        verifyNoInteractions(aiClient);
    }

    @Test
    void deletingTaskKeepsDraftConsistent() throws Exception {
        Fixture f = fixture("Material ist verfügbar", null);
        var before = review(f);
        mvc.perform(post(f.url() + "/tasks/" + before.getElements().getFirst().getId() + "/delete")
                        .param("lockVersion", String.valueOf(before.getLockVersion())).with(user(f.owner())).with(csrf()))
                .andExpect(redirectedUrl(f.reviewUrl()));
        assertThat(review(f).getElements()).hasSize(3);
        assertThat(review(f).getSections().getFirst().getElements())
                .extracting("sortOrder").containsExactly(0, 1, 2);
        application.continueWithPending(f.projectId(), f.owner().userId(), review(f).getLockVersion());
        assertThat(elementCount(f)).isEqualTo(3);
    }

    @Test
    void currentDraftIsRevalidatedAndFailureLeavesNoPartialPlan() throws Exception {
        Fixture f = fixture("Material ist verfügbar", null);
        var before = review(f);
        mvc.perform(post(f.url() + "/apply").with(user(f.owner())).with(csrf()))
                .andExpect(view().name("generation/draft-pending-confirmation"));
        // Simulate persisted domain-invalid dates, which are legal SQL values.
        jdbc.update("update draft_tasks set start_date = ?, due_date = ? where id = ?",
                LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 1), before.getElements().getFirst().getId());
        mvc.perform(post(f.url() + "/continue-with-pending").param("lockVersion", String.valueOf(before.getLockVersion()))
                        .with(user(f.owner())).with(csrf()))
                .andExpect(redirectedUrl(f.reviewUrl())).andExpect(flash().attribute("errorMessage", not(blankOrNullString())));
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
        assertThatThrownBy(() -> application.continueWithPending(
                f.projectId(), f.owner().userId(), review(f).getLockVersion()))
                .isInstanceOf(de.melinadanhier.projectflow.common.exception.DomainValidationException.class);
        assertEmptyPlan(f);
    }

    @Test
    void ownerChecksAndCsrfProtectEveryMutationAndConfirmation() throws Exception {
        Fixture f = fixture("Material ist verfügbar", null);
        var outsider = fixture(null, null).owner();
        var draft = review(f);
        var task = draft.getElements().getFirst();
        var section = draft.getSections().getFirst();
        var milestoneId = UUID.randomUUID();
        mvc.perform(get(f.reviewUrl()).with(user(outsider))).andExpect(status().isNotFound());
        mvc.perform(get(f.reviewUrl())).andExpect(status().is3xxRedirection());
        for (String suffix : List.of("/apply", "/confirm-and-apply", "/continue-with-pending",
                "/elements/" + task.getId() + "/accept", "/elements/" + task.getId() + "/reject",
                "/elements/" + task.getId() + "/reset", "/sections/" + section.getId() + "/accept",
                "/sections/" + section.getId() + "/reject", "/sections/" + section.getId() + "/reset",
                "/sections/" + section.getId(),
                "/tasks/" + task.getId() + "/delete", "/tasks/" + task.getId(),
                "/milestones/" + milestoneId, "/elements/" + task.getId() + "/move",
                "/sections/" + section.getId() + "/move", "/sort-mode")) {
            mvc.perform(post(f.url() + suffix).param("lockVersion", "0").param("title", "Titel").param("priority", "LOW")
                            .param("targetSectionId", section.getId().toString()).param("targetPosition", "0")
                            .param("sortMode", "MANUAL")
                            .with(user(outsider)).with(csrf())).andExpect(status().isNotFound());
            mvc.perform(post(f.url() + suffix).param("lockVersion", "0").with(user(f.owner())))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get(f.url() + "/confirm-and-apply").with(user(f.owner())))
                .andExpect(status().isMethodNotAllowed());
        assertEmptyPlan(f);
    }

    @Test
    void reviewUsesPersistedProjectHeaderAndPrefersSubcategory() throws Exception {
        Fixture f = fixture(null, null);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Project project = projects.findById(f.projectId()).orElseThrow();
            project.setCategory(TemplateCategory.HOME);
            project.setSubcategory(ProjectSubCategory.MOVING);
            project.setStartDate(LocalDate.of(2026, 9, 1));
            project.setEndDate(LocalDate.of(2026, 10, 13));
        });

        mvc.perform(get(f.reviewUrl()).with(user(f.owner())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Testprojekt")))
                .andExpect(content().string(containsString("Umzug")))
                .andExpect(content().string(containsString("01.09.2026")))
                .andExpect(content().string(containsString("13.10.2026")))
                .andExpect(content().string(not(containsString("Prompt-Version"))))
                .andExpect(content().string(not(containsString("Schema-Version"))));

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                projects.findById(f.projectId()).orElseThrow().setSubcategory(null));
        mvc.perform(get(f.reviewUrl()).with(user(f.owner())))
                .andExpect(content().string(containsString("Zuhause")));
    }

    @Test
    void reviewOrdersSectionsAndMixedElementsOnlyByPersistedOrder() {
        Fixture f = fixture(null, null);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            DraftPlan draft = drafts.findByProjectId(f.projectId()).orElseThrow();
            DraftSection existing = draft.getSections().getFirst();
            existing.setSortOrder(20);
            DraftSection first = new DraftSection();
            first.setTitle("Erster Bereich");
            first.setSortOrder(10);
            draft.addSection(first);

            DraftMilestone milestone = new DraftMilestone();
            milestone.setTitle("Zwischenziel");
            milestone.setSortOrder(25);
            draft.addElement(milestone);
            existing.addElement(milestone);
        });
        jdbc.update("update draft_plan_elements set sort_order = case title "
                        + "when 'Aufgabe 1' then 30 when 'Aufgabe 2' then 10 "
                        + "when 'Aufgabe 3' then 20 else 40 end where plan_draft_id = "
                        + "(select id from plan_drafts where project_id = ?) and title like 'Aufgabe %'", f.projectId());

        DraftReviewDto review = review(f);
        assertThat(review.getSections()).extracting("title")
                .containsExactly("Erster Bereich", "Section");
        assertThat(review.getSections().get(1).getElements()).extracting("title")
                .containsExactly("Aufgabe 2", "Aufgabe 3", "Zwischenziel", "Aufgabe 1", "Aufgabe 4");
        assertThat(review.getSections().get(1).getElements()).extracting("manualPosition")
                .containsExactly(0, 1, 2, 3, 4);
    }

    @Test
    void sectionCanBeEditedAndDescriptionCanBeRemovedWithoutChangingOtherDraftContent() throws Exception {
        Fixture f = fixture("Bleibt erhalten", null);
        DraftReviewDto before = review(f);
        var section = before.getSections().getFirst();
        var elementsBefore = before.getElements().stream()
                .map(element -> List.of(element.getId(), element.getTitle(), element.getSortOrder()))
                .toList();

        mvc.perform(post(f.url() + "/sections/" + section.getId())
                        .param("lockVersion", String.valueOf(before.getLockVersion()))
                        .param("title", "Neu geordnet")
                        .param("description", "  Thematischer Bereich  ")
                        .with(user(f.owner())).with(csrf()))
                .andExpect(redirectedUrl(f.reviewUrl()));
        DraftReviewDto updated = review(f);
        assertThat(updated.getSections().getFirst().getTitle()).isEqualTo("Neu geordnet");
        assertThat(updated.getSections().getFirst().getDescription()).isEqualTo("Thematischer Bereich");
        assertThat(updated.getSections().getFirst().getOrigin())
                .isEqualTo(de.melinadanhier.projectflow.planelement.model.ElementOrigin.AI_MODIFIED);
        assertThat(updated.getElements().stream()
                .map(element -> List.of(element.getId(), element.getTitle(), element.getSortOrder())))
                .containsExactlyElementsOf(elementsBefore);

        mvc.perform(post(f.url() + "/sections/" + section.getId())
                        .param("lockVersion", String.valueOf(updated.getLockVersion()))
                        .param("title", "Neu geordnet").param("description", "  ")
                        .with(user(f.owner())).with(csrf()))
                .andExpect(redirectedUrl(f.reviewUrl()));
        assertThat(review(f).getSections().getFirst().getDescription()).isNull();
    }

    @Test
    void reviewStatusFilterKeepsMatchingChildSectionVisible() {
        Fixture f = fixture("Kritische Annahme", null);
        DraftReviewDto initial = review(f);
        assertThat(initial.getTotalElementCount()).isEqualTo(5);
        assertThat(initial.getReviewedElementCount()).isZero();
        assertThat(initial.getPendingElementCount()).isEqualTo(5);

        reviews.acceptSection(f.projectId(), initial.getSections().getFirst().getId(),
                f.owner().userId(), initial.getLockVersion());
        DraftReviewDto afterSection = review(f);
        reviews.rejectElement(f.projectId(), afterSection.getElements().getFirst().getId(),
                f.owner().userId(), afterSection.getLockVersion());

        DraftReviewDto pending = reviews.review(
                f.projectId(), f.owner().userId(), DraftReviewStatus.PENDING);
        assertThat(pending.getElements()).hasSize(3).allMatch(element ->
                element.getReviewStatus() == DraftReviewStatus.PENDING);
        assertThat(pending.getSections()).singleElement().satisfies(section -> {
            assertThat(section.getReviewStatus()).isEqualTo(DraftReviewStatus.ACCEPTED);
            assertThat(section.getElements()).hasSize(3);
        });
        assertThat(pending.getReviewedElementCount()).isEqualTo(2);
        assertThat(pending.getTotalElementCount()).isEqualTo(5);

        DraftReviewDto rejectedCritical = reviews.review(
                f.projectId(), f.owner().userId(), DraftReviewStatus.REJECTED);
        assertThat(rejectedCritical.getElements()).singleElement()
                .extracting("title").isEqualTo("Aufgabe 1");
        assertThat(rejectedCritical.getSections()).singleElement()
                .satisfies(section -> assertThat(section.getElements()).hasSize(1));
    }

    @Test
    void statusChangesAreReversibleAndNeverCascadeFromASectionToItsChildren() {
        Fixture f = fixture(null, null);
        DraftReviewDto current = review(f);
        UUID sectionId = current.getSections().getFirst().getId();
        UUID taskId = current.getElements().getFirst().getId();

        reviews.rejectSection(f.projectId(), sectionId, f.owner().userId(), current.getLockVersion());
        current = review(f);
        assertThat(current.getSections().getFirst().getReviewStatus()).isEqualTo(DraftReviewStatus.REJECTED);
        assertThat(current.getElements()).allMatch(element ->
                element.getReviewStatus() == DraftReviewStatus.PENDING);

        reviews.resetSection(f.projectId(), sectionId, f.owner().userId(), current.getLockVersion());
        current = review(f);
        reviews.acceptElement(f.projectId(), taskId, f.owner().userId(), current.getLockVersion());
        current = review(f);
        assertThat(current.getElements().getFirst().getReviewStatus()).isEqualTo(DraftReviewStatus.ACCEPTED);
        reviews.resetElement(f.projectId(), taskId, f.owner().userId(), current.getLockVersion());
        current = review(f);
        assertThat(current.getElements().getFirst().getReviewStatus()).isEqualTo(DraftReviewStatus.PENDING);
        reviews.rejectElement(f.projectId(), taskId, f.owner().userId(), current.getLockVersion());
        current = review(f);
        reviews.resetElement(f.projectId(), taskId, f.owner().userId(), current.getLockVersion());
        assertThat(review(f).getElements().getFirst().getReviewStatus()).isEqualTo(DraftReviewStatus.PENDING);
    }

    @Test
    void rejectedSectionKeepsAcceptedChildrenAndMaterializesThemWithoutASection() {
        Fixture f = fixture(null, null);
        DraftReviewDto current = review(f);
        UUID sectionId = current.getSections().getFirst().getId();
        reviews.rejectSection(f.projectId(), sectionId, f.owner().userId(), current.getLockVersion());

        current = review(f);
        UUID firstTask = current.getElements().get(0).getId();
        UUID secondTask = current.getElements().get(1).getId();
        reviews.acceptElement(f.projectId(), firstTask, f.owner().userId(), current.getLockVersion());
        current = review(f);
        reviews.acceptElement(f.projectId(), secondTask, f.owner().userId(), current.getLockVersion());
        current = review(f);
        for (int index = 2; index < current.getElements().size(); index++) {
            reviews.rejectElement(f.projectId(), current.getElements().get(index).getId(),
                    f.owner().userId(), current.getLockVersion());
            current = review(f);
        }

        var move = new de.melinadanhier.projectflow.draft.dto.DraftElementMoveForm();
        move.setLockVersion(current.getLockVersion());
        move.setTargetSectionId(null);
        move.setTargetPosition(0);
        reviews.moveElement(f.projectId(), secondTask, f.owner().userId(), move);
        current = review(f);
        assertThat(current.getSections().getFirst().getReviewStatus()).isEqualTo(DraftReviewStatus.REJECTED);
        assertThat(current.getSections().getFirst().getElements()).extracting("id").contains(firstTask);
        assertThat(current.getUnsectionedElements()).extracting("id").containsExactly(secondTask);

        application.apply(f.projectId(), f.owner().userId());

        assertThat(jdbc.queryForObject("select count(*) from plan_sections where plan_container_id = ?",
                Integer.class, f.projectId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from plan_elements where plan_container_id = ?",
                Integer.class, f.projectId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from plan_elements where plan_container_id = ? "
                        + "and plan_section_id is null", Integer.class, f.projectId())).isEqualTo(2);
    }

    @Test
    void acceptedSectionAndElementKeepTheirAssociationWhileRejectedElementsAreOmitted() {
        Fixture f = fixture(null, null);
        DraftReviewDto current = review(f);
        reviews.acceptSection(f.projectId(), current.getSections().getFirst().getId(),
                f.owner().userId(), current.getLockVersion());
        current = review(f);
        reviews.acceptElement(f.projectId(), current.getElements().getFirst().getId(),
                f.owner().userId(), current.getLockVersion());
        current = review(f);
        for (int index = 1; index < current.getElements().size(); index++) {
            reviews.rejectElement(f.projectId(), current.getElements().get(index).getId(),
                    f.owner().userId(), current.getLockVersion());
            current = review(f);
        }

        application.apply(f.projectId(), f.owner().userId());

        assertThat(jdbc.queryForObject("select count(*) from plan_sections where plan_container_id = ?",
                Integer.class, f.projectId())).isOne();
        assertThat(jdbc.queryForObject("select count(*) from plan_elements where plan_container_id = ? "
                        + "and plan_section_id is not null", Integer.class, f.projectId())).isOne();
    }

    @Test
    void missingDraftProjectAndForeignSectionReturnNotFound() throws Exception {
        Fixture f = fixture(null, null);
        Fixture other = fixture(null, null);
        var otherSection = review(other).getSections().getFirst();

        mvc.perform(post(f.url() + "/sections/" + otherSection.getId())
                        .param("lockVersion", String.valueOf(review(f).getLockVersion()))
                        .param("title", "Nicht erlaubt").with(user(f.owner())).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/projects/" + UUID.randomUUID() + "/draft/review").with(user(f.owner())))
                .andExpect(status().isNotFound());

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                drafts.delete(drafts.findByProjectId(f.projectId()).orElseThrow()));
        mvc.perform(get(f.reviewUrl()).with(user(f.owner())))
                .andExpect(status().isNotFound());
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
                    GeneratedElementOrigin.AI_INFERRED, index)).toList();
            var contents = generatedPlanMapper.map(new GeneratedPlanResponse(List.of(new GeneratedSection(
                    "section", "Section", null, 1, tasks, List.of()))));
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
        String reviewUrl() { return url() + "/review"; }
    }
}
