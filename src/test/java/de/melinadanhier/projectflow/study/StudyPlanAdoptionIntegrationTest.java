package de.melinadanhier.projectflow.study;

import de.melinadanhier.projectflow.draft.model.*;
import de.melinadanhier.projectflow.draft.repository.DraftRepository;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflow;
import de.melinadanhier.projectflow.generation.model.workflow.AiWorkflowCompletionToken;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import de.melinadanhier.projectflow.generation.repository.AiWorkflowCompletionTokenRepository;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.model.lifecycle.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.model.lifecycle.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.project.model.membership.ProjectMember;
import de.melinadanhier.projectflow.plancontainer.project.model.membership.ProjectMemberRole;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import de.melinadanhier.projectflow.planelement.model.ElementOrigin;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import de.melinadanhier.projectflow.security.service.AuthenticatedUser;
import de.melinadanhier.projectflow.study.domain.StudyEventType;
import de.melinadanhier.projectflow.study.domain.StudyPhase;
import de.melinadanhier.projectflow.study.domain.StudySession;
import de.melinadanhier.projectflow.study.domain.StudySessionStatus;
import de.melinadanhier.projectflow.study.repository.StudyEventRepository;
import de.melinadanhier.projectflow.study.repository.StudySessionRepository;
import de.melinadanhier.projectflow.study.service.StudyTrackingService;
import de.melinadanhier.projectflow.user.model.User;
import de.melinadanhier.projectflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class StudyPlanAdoptionIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private DraftRepository draftRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private StudySessionRepository studySessionRepository;
    @Autowired private StudyEventRepository studyEventRepository;
    @Autowired private AiPlanGenerationWorkflowRepository workflowRepository;
    @Autowired private AiWorkflowCompletionTokenRepository completionTokenRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private de.melinadanhier.projectflow.generation.event.listener.AiGenerationRequestedEventListener generationListener;
    @MockitoBean private de.melinadanhier.projectflow.generation.event.listener.AiPreCheckRequestedEventListener preCheckListener;

    @Test
    void planAdoptionRebindsStudySessionFromPreviousDraftToAdoptedProjectAndEnablesTaskOneCompletion() throws Exception {
        DraftFixture draftA = createDraftFixture("draft-a");
        DraftFixture draftB = createDraftFixture("draft-b");

        // User is owner of draft B
        User user = userRepository.findById(draftB.ownerId()).orElseThrow();
        AuthenticatedUser principal = new AuthenticatedUser(
                user.getId(), user.getEmail(), user.getPasswordHash(), true, user.getDisplayName());

        // StudySession currently points to draft A
        StudySession initialSession = new StudySession();
        initialSession.setStatus(StudySessionStatus.ACTIVE);
        initialSession.setCurrentPhase(StudyPhase.TASK_1);
        initialSession.setStartedAt(Instant.now());
        initialSession.setProjectId(draftA.projectId());
        StudySession studySession = studySessionRepository.saveAndFlush(initialSession);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(StudyTrackingService.SESSION_ATTRIBUTE, studySession.getId());
        session.setAttribute(StudyTrackingService.PHASE_ATTRIBUTE, "TASK_1");

        // Step 1: User adopts draft B
        mvc.perform(post("/projects/" + draftB.projectId() + "/draft/continue-with-pending")
                        .param("draftId", draftB.draftId().toString())
                        .param("lockVersion", "0")
                        .session(session)
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/" + draftB.projectId() + "/plan"));

        // Verify: StudySession now points to draft B
        StudySession updatedSession = studySessionRepository.findById(studySession.getId()).orElseThrow();
        assertThat(updatedSession.getProjectId()).isEqualTo(draftB.projectId());

        // Verify: Project B is in OVERVIEW location
        Project adoptedProject = projectRepository.findById(draftB.projectId()).orElseThrow();
        assertThat(adoptedProject.getLocation()).isEqualTo(ProjectLocation.OVERVIEW);

        // Verify: Event PLAN_ADOPTED was recorded
        boolean hasPlanAdopted = studyEventRepository.findAll().stream()
                .anyMatch(event -> event.getStudySession().getId().equals(studySession.getId())
                        && event.getEventType() == StudyEventType.PLAN_ADOPTED);
        assertThat(hasPlanAdopted).isTrue();

        // Step 2: User completes Task 1
        mvc.perform(post("/study/task-1/complete")
                        .session(session)
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/" + draftB.projectId() + "/plan"));

        // Verify: StudySession moved to TASK_2
        StudySession phaseTwoSession = studySessionRepository.findById(studySession.getId()).orElseThrow();
        assertThat(phaseTwoSession.getCurrentPhase()).isEqualTo(StudyPhase.TASK_2);
    }

    @Test
    void completeTaskOneWithoutPlanAdoptionRedirectsWithErrorMessageWithout500() throws Exception {
        DraftFixture draftA = createDraftFixture("draft-unadopted");
        User user = userRepository.findById(draftA.ownerId()).orElseThrow();
        AuthenticatedUser principal = new AuthenticatedUser(
                user.getId(), user.getEmail(), user.getPasswordHash(), true, user.getDisplayName());

        StudySession studySession = new StudySession();
        studySession.setStatus(StudySessionStatus.ACTIVE);
        studySession.setCurrentPhase(StudyPhase.TASK_1);
        studySession.setStartedAt(Instant.now());
        studySession.setProjectId(draftA.projectId());
        studySession = studySessionRepository.saveAndFlush(studySession);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(StudyTrackingService.SESSION_ATTRIBUTE, studySession.getId());
        session.setAttribute(StudyTrackingService.PHASE_ATTRIBUTE, "TASK_1");

        // Attempt to complete task 1 while draftA is still in DRAFT location
        mvc.perform(post("/study/task-1/complete")
                        .session(session)
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/" + draftA.projectId() + "/draft/review"))
                .andExpect(flash().attribute("errorMessage",
                        "Aufgabe 1 kann erst abgeschlossen werden, wenn ein Plan übernommen wurde."));

        // Verify: Phase is still TASK_1
        StudySession currentSession = studySessionRepository.findById(studySession.getId()).orElseThrow();
        assertThat(currentSession.getCurrentPhase()).isEqualTo(StudyPhase.TASK_1);
    }

    @Test
    void planAdoptionWorksNormallyWithoutActiveStudySession() throws Exception {
        DraftFixture draft = createDraftFixture("no-study");
        User user = userRepository.findById(draft.ownerId()).orElseThrow();
        AuthenticatedUser principal = new AuthenticatedUser(
                user.getId(), user.getEmail(), user.getPasswordHash(), true, user.getDisplayName());

        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/projects/" + draft.projectId() + "/draft/continue-with-pending")
                        .param("draftId", draft.draftId().toString())
                        .param("lockVersion", "0")
                        .session(session)
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/" + draft.projectId() + "/plan"))
                .andExpect(flash().attribute("successMessage", "Der KI-Entwurf wurde übernommen."));

        Project project = projectRepository.findById(draft.projectId()).orElseThrow();
        assertThat(project.getLocation()).isEqualTo(ProjectLocation.OVERVIEW);
    }

    @Test
    void regressionPreCheckEditProducesNewProjectWhichCanBeAdoptedAndCompleted() throws Exception {
        // Step 1: Initial draft project A created in wizard
        DraftFixture draftA = createDraftFixture("precheck-a");
        User user = userRepository.findById(draftA.ownerId()).orElseThrow();
        AuthenticatedUser principal = new AuthenticatedUser(
                user.getId(), user.getEmail(), user.getPasswordHash(), true, user.getDisplayName());

        StudySession studySession = new StudySession();
        studySession.setStatus(StudySessionStatus.ACTIVE);
        studySession.setCurrentPhase(StudyPhase.TASK_1);
        studySession.setStartedAt(Instant.now());
        studySession.setProjectId(draftA.projectId());
        studySession = studySessionRepository.saveAndFlush(studySession);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(StudyTrackingService.SESSION_ATTRIBUTE, studySession.getId());
        session.setAttribute(StudyTrackingService.PHASE_ATTRIBUTE, "TASK_1");

        // Step 2: User returns from Pre-Check and creates Project B (simulated by creating draftB for same user)
        DraftFixture draftB = createDraftFixtureForOwner(user, "precheck-b");

        // Prior to our fix, studySession.projectId would remain stuck on draftA because draftA still exists
        assertThat(studySessionRepository.findById(studySession.getId()).orElseThrow().getProjectId())
                .isEqualTo(draftA.projectId());

        // Step 3: User adopts draft B
        mvc.perform(post("/projects/" + draftB.projectId() + "/draft/continue-with-pending")
                        .param("draftId", draftB.draftId().toString())
                        .param("lockVersion", "0")
                        .session(session)
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/" + draftB.projectId() + "/plan"));

        // Step 4: Verify study session was rebound to draft B
        assertThat(studySessionRepository.findById(studySession.getId()).orElseThrow().getProjectId())
                .isEqualTo(draftB.projectId());

        // Step 5: User completes Task 1 - should succeed now without throwing 500
        mvc.perform(post("/study/task-1/complete")
                        .session(session)
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/" + draftB.projectId() + "/plan"));

        StudySession finalSession = studySessionRepository.findById(studySession.getId()).orElseThrow();
        assertThat(finalSession.getCurrentPhase()).isEqualTo(StudyPhase.TASK_2);
    }

    private DraftFixture createDraftFixture(String prefix) {
        User owner = new User();
        owner.setEmail(prefix + "-" + UUID.randomUUID() + "@example.org");
        owner.setDisplayName("Study Owner");
        owner.setPasswordHash("hash");
        owner.setEnabled(true);
        userRepository.saveAndFlush(owner);
        return createDraftFixtureForOwner(owner, prefix);
    }

    private DraftFixture createDraftFixtureForOwner(User owner, String prefix) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Project project = new Project();
            project.setTitle("Studienprojekt " + prefix);
            project.setCreationType(CreationType.AI);
            project.setLocation(ProjectLocation.DRAFT);
            ProjectMember membership = new ProjectMember();
            membership.setUser(owner);
            membership.setRole(ProjectMemberRole.OWNER);
            membership.setActive(true);
            project.addMembership(membership);
            projectRepository.saveAndFlush(project);

            DraftPlan draft = new DraftPlan();
            draft.setStatus(DraftPlanStatus.READY_FOR_REVIEW);
            project.attachDraft(draft);

            DraftSection section = new DraftSection();
            section.setTitle("Vorbereitung");
            section.setSortOrder(10);
            section.setReviewStatus(DraftReviewStatus.PENDING);
            section.setOrigin(ElementOrigin.AI);
            draft.addSection(section);

            DraftTask task = new DraftTask();
            task.setTitle("Kisten packen");
            task.setSortOrder(10);
            task.setReviewStatus(DraftReviewStatus.PENDING);
            task.setPriority(TaskPriority.HIGH);
            task.setEstimatedMinutes(120);
            task.setOrigin(ElementOrigin.AI);
            draft.addElement(task);
            section.addElement(task);

            draftRepository.saveAndFlush(draft);

            var workflow = AiPlanGenerationWorkflow.create(
                    project, "{\"title\":\"" + prefix + "\"}", "1.0", UUID.randomUUID(), Instant.now(), "v1");
            workflowRepository.saveAndFlush(workflow);
            jdbc.update("update ai_plan_generation_workflows set status = 'GENERATION_COMPLETED' where id = ?", workflow.getId());
            completionTokenRepository.saveAndFlush(
                    AiWorkflowCompletionToken.create(UUID.randomUUID(), workflow));

            return new DraftFixture(project.getId(), draft.getId(), owner.getId());
        });
    }

    private record DraftFixture(UUID projectId, UUID draftId, UUID ownerId) { }
}
