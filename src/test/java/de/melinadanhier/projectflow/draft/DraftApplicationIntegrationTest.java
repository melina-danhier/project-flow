package de.melinadanhier.projectflow.draft;

import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.draft.dto.application.DraftApplyStatus;
import de.melinadanhier.projectflow.draft.model.*;
import de.melinadanhier.projectflow.draft.repository.DraftRepository;
import de.melinadanhier.projectflow.draft.service.DraftApplicationService;
import de.melinadanhier.projectflow.plancontainer.project.model.*;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import de.melinadanhier.projectflow.planelement.model.ElementOrigin;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import de.melinadanhier.projectflow.user.model.User;
import de.melinadanhier.projectflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class DraftApplicationIntegrationTest {
    @Autowired DraftApplicationService applications;
    @Autowired
    DraftRepository drafts;
    @Autowired ProjectRepository projects;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MockMvc mvc;
    @Autowired de.melinadanhier.projectflow.generation.service.workflow.AiGenerationWorkflowService generations;
    @Autowired de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository workflows;
    @MockitoSpyBean de.melinadanhier.projectflow.draft.service.DraftPlanAdoptionFactory adoptionFactory;
    @MockitoBean de.melinadanhier.projectflow.generation.event.listener.AiGenerationRequestedEventListener generationListener;

    @Test
    void adoptsReviewedGraphWithNormalizedSharedOrderOriginsFieldsAndDependencies() throws Exception {
        Fixture fixture = richFixture();
        var summary = applications.summarize(fixture.projectId(), fixture.ownerId());
        assertThat(summary.pendingElementCount()).isEqualTo(1);
        assertThat(summary.omittedDependencyCount()).isEqualTo(1);
        assertThat(summary.includedSectionCount()).isEqualTo(2);
        assertThat(summary.includedElementCount()).isEqualTo(5);
        assertThat(applications.apply(fixture.projectId(), fixture.ownerId()))
                .satisfies(result -> {
                    assertThat(result.status()).isEqualTo(DraftApplyStatus.PENDING_CONFIRMATION_REQUIRED);
                    assertThat(result.summary()).isEqualTo(summary);
                });

        applications.confirmAndApply(fixture.projectId(), fixture.draftId(), fixture.ownerId(),
                summary.lockVersion(), false);

        assertThat(jdbc.queryForList("select title from plan_sections where plan_container_id = ? order by sort_order",
                String.class, fixture.projectId())).containsExactly("Arbeitsbereich", "Leerer Bereich");
        assertThat(jdbc.queryForObject("select count(*) from plan_sections where plan_container_id = ? and title = 'Leerer Bereich'",
                Integer.class, fixture.projectId())).isOne();
        assertThat(jdbc.queryForList("select title from plan_elements where plan_container_id = ? and plan_section_id is null order by sort_order",
                String.class, fixture.projectId())).containsExactly("Schon ohne Bereich", "Verworfenes Zwischenziel", "Kind aus verworfenem Bereich");
        assertThat(jdbc.queryForList("select pe.title from plan_elements pe join plan_sections ps on ps.id = pe.plan_section_id "
                        + "where pe.plan_container_id = ? and ps.title = 'Arbeitsbereich' order by pe.sort_order",
                String.class, fixture.projectId())).containsExactly("Offene Aufgabe", "Angenommener Meilenstein");
        assertThat(jdbc.queryForObject("select count(*) from plan_elements where plan_container_id = ? and title = 'Verworfene Aufgabe'",
                Integer.class, fixture.projectId())).isZero();
        assertThat(jdbc.queryForMap("select t.status, t.assignee_id, t.priority, t.estimated_hours, pe.origin "
                + "from tasks t join plan_elements pe on pe.id = t.id where pe.plan_container_id = ? and pe.title = 'Offene Aufgabe'",
                fixture.projectId())).containsEntry("STATUS", "OPEN").containsEntry("PRIORITY", "HIGH")
                .containsEntry("ESTIMATED_HOURS", 8).containsEntry("ORIGIN", "AI_MODIFIED")
                .containsEntry("ASSIGNEE_ID", null);
        assertThat(jdbc.queryForObject("select count(*) from task_prerequisites tp join plan_elements pe "
                + "on pe.id = tp.successor_task_id where pe.plan_container_id = ?", Integer.class, fixture.projectId())).isOne();

        var applied = drafts.findById(fixture.draftId()).orElseThrow();
        assertThat(applied.getStatus()).isEqualTo(DraftPlanStatus.APPLIED);
        assertThat(applied.getAppliedAt()).isNotNull();
        assertThat(projects.findById(fixture.projectId()).orElseThrow().getStatus()).isEqualTo(ProjectStatus.ACTIVE);
        assertThat(applications.apply(fixture.projectId(), fixture.ownerId()).status())
                .isEqualTo(DraftApplyStatus.APPLIED);

        applications.confirmAndApply(fixture.projectId(), fixture.draftId(), fixture.ownerId(),
                summary.lockVersion(), false);
        assertThat(jdbc.queryForObject("select count(*) from plan_elements where plan_container_id = ?",
                Integer.class, fixture.projectId())).isEqualTo(5);

        User owner = users.findById(fixture.ownerId()).orElseThrow();
        var principal = new de.melinadanhier.projectflow.security.service.AuthenticatedUser(
                owner.getId(), owner.getEmail(), owner.getPasswordHash(), true);
        assertThat(mvc.perform(post("/projects/" + fixture.projectId() + "/draft/continue-with-pending")
                        .param("draftId", UUID.randomUUID().toString())
                        .param("lockVersion", String.valueOf(summary.lockVersion()))
                        .with(user(principal)).with(csrf()))
                .andExpect(status().isConflict()).andReturn().getResolvedException())
                .isInstanceOf(de.melinadanhier.projectflow.draft.service.DraftVersionConflictException.class);
        assertThat(jdbc.queryForObject("select count(*) from plan_elements where plan_container_id = ?",
                Integer.class, fixture.projectId())).isEqualTo(5);
    }

    @Test
    void emptyDraftRequiresExplicitServerSideConfirmation() {
        Fixture fixture = rejectedFixture();
        var summary = applications.summarize(fixture.projectId(), fixture.ownerId());
        assertThat(summary.empty()).isTrue();
        assertThat(applications.apply(fixture.projectId(), fixture.ownerId()))
                .satisfies(result -> {
                    assertThat(result.status()).isEqualTo(DraftApplyStatus.EMPTY_DRAFT_CONFIRMATION_REQUIRED);
                    assertThat(result.summary()).isEqualTo(summary);
                });

        assertThatThrownBy(() -> applications.confirmAndApply(fixture.projectId(), fixture.draftId(),
                fixture.ownerId(), summary.lockVersion(), false)).isInstanceOf(DomainValidationException.class);
        assertThat(projects.findById(fixture.projectId()).orElseThrow().getStatus()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(drafts.findById(fixture.draftId()).orElseThrow().getStatus()).isNotEqualTo(DraftPlanStatus.APPLIED);

        applications.confirmEmpty(fixture.projectId(), fixture.draftId(), fixture.ownerId(),
                summary.lockVersion());
        assertThat(projects.findById(fixture.projectId()).orElseThrow().getStatus()).isEqualTo(ProjectStatus.ACTIVE);
        assertThat(jdbc.queryForObject("select count(*) from plan_elements where plan_container_id = ?",
                Integer.class, fixture.projectId())).isZero();
    }

    @Test
    void emptyDraftDialogOffersRegenerationAndExplicitEmptyActivation() throws Exception {
        Fixture fixture = rejectedFixture();
        User owner = users.findById(fixture.ownerId()).orElseThrow();
        var principal = new de.melinadanhier.projectflow.security.service.AuthenticatedUser(
                owner.getId(), owner.getEmail(), owner.getPasswordHash(), true);
        mvc.perform(post("/projects/" + fixture.projectId() + "/draft/apply")
                        .with(user(principal)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("generation/draft-pending-confirmation"))
                .andExpect(content().string(containsString("Neu generieren")))
                .andExpect(content().string(containsString("Leeres Projekt erstellen")))
                .andExpect(content().string(containsString("/draft/confirm-empty")));
    }

    @Test
    void regenerationKeepsProjectInactiveAndUsesExistingWorkflowTransition() {
        Fixture fixture = rejectedFixture();
        UUID workflowId = new TransactionTemplate(transactionManager).execute(status -> {
            Project project = projects.findById(fixture.projectId()).orElseThrow();
            var workflow = de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflow.create(
                    project, "{}", "test", UUID.randomUUID(), Instant.now(), "v1");
            workflows.saveAndFlush(workflow);
            jdbc.update("update ai_plan_generation_workflows set status = 'GENERATION_COMPLETED' where id = ?", workflow.getId());
            return workflow.getId();
        });
        long version = applications.summarize(fixture.projectId(), fixture.ownerId()).lockVersion();

        assertThat(generations.regenerateDraft(fixture.projectId(), fixture.draftId(),
                fixture.ownerId(), version)).isEqualTo(workflowId);

        assertThat(projects.findById(fixture.projectId()).orElseThrow().getStatus()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(drafts.findById(fixture.draftId())).isEmpty();
        assertThat(workflows.findById(workflowId).orElseThrow().getStatus())
                .isEqualTo(de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus.GENERATION_PENDING);
        verify(generationListener).onGenerationRequested(
                any(de.melinadanhier.projectflow.generation.event.AiGenerationRequestedEvent.class));
    }

    @Test
    void failureAfterGraphCreationRollsBackEveryAdoptionChange() {
        Fixture fixture = richFixture();
        var summary = applications.summarize(fixture.projectId(), fixture.ownerId());
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("simulierter Persistenzfehler");
        }).when(adoptionFactory).adopt(any(), any());
        try {
            assertThatThrownBy(() -> applications.confirmAndApply(fixture.projectId(), fixture.draftId(),
                    fixture.ownerId(), summary.lockVersion(), false)).isInstanceOf(IllegalStateException.class);
        } finally {
            reset(adoptionFactory);
        }
        assertThat(jdbc.queryForObject("select count(*) from plan_elements where plan_container_id = ?",
                Integer.class, fixture.projectId())).isZero();
        assertThat(projects.findById(fixture.projectId()).orElseThrow().getStatus()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(drafts.findById(fixture.draftId()).orElseThrow().getStatus())
                .isIn(DraftPlanStatus.READY_FOR_REVIEW, DraftPlanStatus.IN_REVIEW);
    }

    @Test
    void persistenceFailureDuringApplicationReturnsToUnchangedDraft() throws Exception {
        Fixture fixture = richFixture();
        var summary = applications.summarize(fixture.projectId(), fixture.ownerId());
        User owner = users.findById(fixture.ownerId()).orElseThrow();
        var principal = new de.melinadanhier.projectflow.security.service.AuthenticatedUser(
                owner.getId(), owner.getEmail(), owner.getPasswordHash(), true);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new DataIntegrityViolationException("simulierter Persistenzfehler");
        }).when(adoptionFactory).adopt(any(), any());

        try {
            mvc.perform(post("/projects/" + fixture.projectId() + "/draft/continue-with-pending")
                            .param("draftId", fixture.draftId().toString())
                            .param("lockVersion", String.valueOf(summary.lockVersion()))
                            .with(user(principal)).with(csrf()))
                    .andExpect(redirectedUrl("/projects/" + fixture.projectId() + "/draft/review"))
                    .andExpect(flash().attribute("errorMessage", containsString("Entwurf blieb unverändert")));
        } finally {
            reset(adoptionFactory);
        }

        assertThat(jdbc.queryForObject("select count(*) from plan_elements where plan_container_id = ?",
                Integer.class, fixture.projectId())).isZero();
        assertThat(projects.findById(fixture.projectId()).orElseThrow().getStatus()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(drafts.findById(fixture.draftId()).orElseThrow().getStatus())
                .isIn(DraftPlanStatus.READY_FOR_REVIEW, DraftPlanStatus.IN_REVIEW);
    }

    private Fixture richFixture() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Project project = project("atomic-rich");
            DraftPlan draft = draft(project);

            DraftSection accepted = section("Arbeitsbereich", 10, DraftReviewStatus.ACCEPTED);
            DraftSection empty = section("Leerer Bereich", 20, DraftReviewStatus.PENDING);
            // It remains included but does not add to the pending count asserted above.
            empty.setReviewStatus(DraftReviewStatus.ACCEPTED);
            DraftSection rejected = section("Verworfener Bereich", 30, DraftReviewStatus.REJECTED);
            draft.addSection(accepted); draft.addSection(empty); draft.addSection(rejected);

            DraftTask pending = task("Offene Aufgabe", 2, DraftReviewStatus.PENDING);
            pending.setDescription("Inhalt"); pending.setPriority(TaskPriority.HIGH);
            pending.setEstimatedHours(8); pending.setStartDate(LocalDate.of(2026, 9, 1));
            pending.setDueDate(LocalDate.of(2026, 9, 3)); pending.setOrigin(ElementOrigin.AI_MODIFIED);
            DraftMilestone milestone = milestone("Angenommener Meilenstein", 5, DraftReviewStatus.ACCEPTED);
            DraftTask omitted = task("Verworfene Aufgabe", 9, DraftReviewStatus.REJECTED);
            accepted.addElement(pending); accepted.addElement(milestone); accepted.addElement(omitted);

            DraftTask unsectioned = task("Schon ohne Bereich", 99, DraftReviewStatus.ACCEPTED);
            DraftMilestone orphanMilestone = milestone("Verworfenes Zwischenziel", 1, DraftReviewStatus.ACCEPTED);
            DraftTask orphanTask = task("Kind aus verworfenem Bereich", 3, DraftReviewStatus.ACCEPTED);
            rejected.addElement(orphanMilestone); rejected.addElement(orphanTask);
            for (DraftPlanElement element : List.of(pending, milestone, omitted, unsectioned, orphanMilestone, orphanTask)) {
                draft.addElement(element);
            }
            pending.addPrerequisite(unsectioned);
            pending.addPrerequisite(omitted);
            drafts.saveAndFlush(draft);
            return new Fixture(project.getId(), draft.getId(), project.getMemberships().iterator().next().getUser().getId());
        });
    }

    private Fixture rejectedFixture() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Project project = project("atomic-empty");
            DraftPlan draft = draft(project);
            DraftSection section = section("Nein", 0, DraftReviewStatus.REJECTED);
            DraftTask task = task("Auch nein", 0, DraftReviewStatus.REJECTED);
            draft.addSection(section); draft.addElement(task); section.addElement(task);
            drafts.saveAndFlush(draft);
            return new Fixture(project.getId(), draft.getId(), project.getMemberships().iterator().next().getUser().getId());
        });
    }

    private Project project(String prefix) {
        User owner = new User();
        owner.setEmail(prefix + "-" + UUID.randomUUID() + "@example.org");
        owner.setDisplayName("Owner"); owner.setPasswordHash("hash"); owner.setEnabled(true);
        users.saveAndFlush(owner);
        Project project = new Project(); project.setTitle("Projekt"); project.setCreationType(CreationType.AI);
        project.setStatus(ProjectStatus.DRAFT); project.setLocation(ProjectLocation.DRAFT);
        ProjectMember membership = new ProjectMember(); membership.setUser(owner);
        membership.setRole(ProjectMemberRole.OWNER); membership.setActive(true); project.addMembership(membership);
        return projects.saveAndFlush(project);
    }

    private DraftPlan draft(Project project) {
        DraftPlan draft = new DraftPlan(); draft.setStatus(DraftPlanStatus.READY_FOR_REVIEW); project.attachDraft(draft);
        return draft;
    }

    private DraftSection section(String title, int order, DraftReviewStatus review) {
        DraftSection value = new DraftSection(); value.setTitle(title); value.setSortOrder(order); value.setReviewStatus(review);
        value.setOrigin(ElementOrigin.AI); return value;
    }

    private DraftTask task(String title, int order, DraftReviewStatus review) {
        DraftTask value = new DraftTask(); value.setTitle(title); value.setSortOrder(order); value.setReviewStatus(review);
        value.setOrigin(ElementOrigin.AI); return value;
    }

    private DraftMilestone milestone(String title, int order, DraftReviewStatus review) {
        DraftMilestone value = new DraftMilestone(); value.setTitle(title); value.setSortOrder(order); value.setReviewStatus(review);
        value.setOrigin(ElementOrigin.AI); return value;
    }

    private record Fixture(UUID projectId, UUID draftId, UUID ownerId) { }
}
