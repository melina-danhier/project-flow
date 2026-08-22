package de.melinadanhier.projectflow.draft;

import de.melinadanhier.projectflow.ai.model.generation.GeneratedElementOrigin;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedMilestone;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPhase;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanMetadata;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedTask;
import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.draft.model.DraftPlan;
import de.melinadanhier.projectflow.draft.model.DraftPlanStatus;
import de.melinadanhier.projectflow.draft.model.DraftSection;
import de.melinadanhier.projectflow.draft.model.DraftTask;
import de.melinadanhier.projectflow.draft.repository.PlanDraftRepository;
import de.melinadanhier.projectflow.draft.service.DraftApplicationService;
import de.melinadanhier.projectflow.draft.service.DraftReviewService;
import de.melinadanhier.projectflow.draft.service.PlanDraftMaterializationService;
import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMember;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMemberRole;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectStatus;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import de.melinadanhier.projectflow.planelement.model.ElementOrigin;
import de.melinadanhier.projectflow.planelement.model.Task;
import de.melinadanhier.projectflow.user.model.User;
import de.melinadanhier.projectflow.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DraftServicesIntegrationTest {

    @Autowired
    private PlanDraftMaterializationService materializationService;

    @Autowired
    private DraftReviewService reviewService;

    @Autowired
    private DraftApplicationService applicationService;

    @Autowired
    private PlanDraftRepository draftRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void readyDraftIsNotOverwritten() {
        Project project = projectWithOwner("ready-draft-owner@example.org");
        DraftPlan draft = draft(project, DraftPlanStatus.READY_FOR_REVIEW);
        DraftSection oldSection = new DraftSection();
        oldSection.setTitle("Bereits geprüft");
        draft.addSection(oldSection);
        draftRepository.saveAndFlush(draft);

        DraftPlan result = materializationService.materialize(project, generatedPlan());

        assertThat(result.getId()).isEqualTo(draft.getId());
        assertThat(result.getSections()).singleElement()
                .extracting(DraftSection::getTitle).isEqualTo("Bereits geprüft");
        assertThat(result.getAttemptCount()).isZero();
    }

    @Test
    void nonReadyDraftReplacesAllContentsAndDeletesOrphans() {
        Project project = projectWithOwner("replace-draft-owner@example.org");
        DraftPlan draft = draft(project, DraftPlanStatus.GENERATING);
        DraftSection oldSection = new DraftSection();
        oldSection.setTitle("Alt");
        draft.addSection(oldSection);
        DraftTask oldTask = new DraftTask();
        oldTask.setTitle("Alte Aufgabe");
        draft.addElement(oldTask);
        oldSection.addElement(oldTask);
        draftRepository.saveAndFlush(draft);
        var oldSectionId = oldSection.getId();
        var oldTaskId = oldTask.getId();

        DraftPlan result = materializationService.materialize(project, generatedPlan());
        entityManager.flush();
        entityManager.clear();

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from draft_sections where id = ?", Integer.class, oldSectionId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from draft_plan_elements where id = ?", Integer.class, oldTaskId)).isZero();
        DraftPlan reloaded = draftRepository.findById(result.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(DraftPlanStatus.READY_FOR_REVIEW);
        assertThat(reloaded.getSchemaVersion()).isEqualTo("generated-plan-v1");
        assertThat(reloaded.getSections()).singleElement().satisfies(section -> {
            assertThat(section.getTitle()).isEqualTo("Neue Phase");
            assertThat(section.getDescription()).isEqualTo("Phasenbeschreibung");
            assertThat(section.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
            assertThat(section.getEndDate()).isEqualTo(LocalDate.of(2026, 9, 20));
        });
        assertThat(reloaded.getElements()).hasSize(2);
        assertThat(reloaded.getElements()).allSatisfy(element ->
                assertThat(element.getDraftSection().getId()).isEqualTo(reloaded.getSections().getFirst().getId()));
        DraftTask task = reloaded.getElements().stream()
                .filter(DraftTask.class::isInstance).map(DraftTask.class::cast).findFirst().orElseThrow();
        assertThat(task.getDescription()).isEqualTo("Aufgabenbeschreibung");
        assertThat(task.getEstimatedHours()).isEqualTo(4);
        assertThat(task.getCriticalAssumption()).isEqualTo("Material ist verfügbar");
        assertThat(task.getAiOrigin()).isEqualTo(GeneratedElementOrigin.USER_INPUT);
    }

    @Test
    void reviewRequiresTheProjectOwner() {
        Project project = projectWithOwner("review-draft-owner@example.org");
        User outsider = user("review-draft-outsider@example.org");
        DraftPlan draft = draft(project, DraftPlanStatus.READY_FOR_REVIEW);
        draftRepository.saveAndFlush(draft);

        assertThat(reviewService.review(project.getId(), project.getMemberships().iterator().next().getUser().getId()))
                .extracting("id", "projectId")
                .containsExactly(draft.getId(), project.getId());
        assertThatThrownBy(() -> reviewService.review(project.getId(), outsider.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void applyingDraftIsRejectedWhenTheActivePlanAlreadyContainsElements() {
        Project project = projectWithOwner("occupied-plan-owner@example.org");
        DraftPlan draft = draft(project, DraftPlanStatus.READY_FOR_REVIEW);
        draftRepository.saveAndFlush(draft);
        Task existingTask = new Task();
        existingTask.setTitle("Bestehende Aufgabe");
        existingTask.setOrigin(ElementOrigin.USER);
        project.addElement(existingTask);
        projectRepository.saveAndFlush(project);

        assertThatThrownBy(() -> applicationService.apply(
                project.getId(), project.getMemberships().iterator().next().getUser().getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("bereits Inhalte");
    }

    private Project projectWithOwner(String email) {
        User owner = user(email);
        Project project = new Project();
        project.setTitle("KI-Projekt");
        project.setCreationType(CreationType.AI);
        project.setStatus(ProjectStatus.DRAFT);
        project.setLocation(ProjectLocation.DRAFT);
        ProjectMember membership = new ProjectMember();
        membership.setUser(owner);
        membership.setRole(ProjectMemberRole.OWNER);
        membership.setActive(true);
        project.addMembership(membership);
        return projectRepository.saveAndFlush(project);
    }

    private User user(String email) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName("Draft Service Test");
        user.setPasswordHash("$2a$12$test-hash");
        user.setEnabled(true);
        return userRepository.saveAndFlush(user);
    }

    private DraftPlan draft(Project project, DraftPlanStatus status) {
        DraftPlan draft = new DraftPlan();
        draft.setProject(project);
        draft.setStatus(status);
        draft.setPromptVersion("generation-v1");
        draft.setSchemaVersion("generated-plan-v1");
        project.attachDraft(draft);
        return draft;
    }

    private GeneratedPlanResponse generatedPlan() {
        return new GeneratedPlanResponse(
                new GeneratedPlanMetadata("Neuer Entwurf", List.of("Eine Annahme")),
                List.of(new GeneratedPhase(
                        "phase-new", "Neue Phase", "Phasenbeschreibung",
                        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20), 3,
                        List.of(new GeneratedTask(
                                "task-new", "Neue Aufgabe", "Aufgabenbeschreibung", 4,
                                LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 10),
                                "Material ist verfügbar", GeneratedElementOrigin.USER_INPUT, 1)),
                        List.of(new GeneratedMilestone(
                                "milestone-new", "Neuer Meilenstein",
                                LocalDate.of(2026, 9, 20), 2)))));
    }
}
