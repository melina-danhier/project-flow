package de.melinadanhier.projectflow.generation.service.workflow;

import de.melinadanhier.projectflow.generation.persistence.AiWorkflowPayloadCodec;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.generation.event.AiPreCheckRequestedEvent;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflow;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.model.workflow.AiWorkflowCompletion;
import de.melinadanhier.projectflow.generation.model.workflow.AiWorkflowCompletionToken;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import de.melinadanhier.projectflow.generation.repository.AiWorkflowCompletionTokenRepository;
import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMember;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMemberRole;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectStatus;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.user.model.User;
import de.melinadanhier.projectflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AiWorkflowInitializationService {

    public static final String SNAPSHOT_VERSION = "ai-wizard-v2";
    public static final String CONSENT_VERSION = "v1";

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final AiPlanGenerationWorkflowRepository workflowRepository;
    private final AiWorkflowCompletionTokenRepository completionTokenRepository;
    private final AiWorkflowPayloadCodec snapshotCodec;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public AiWorkflowCompletion create(
            AiWizardSnapshot snapshot,
            UUID completionToken,
            UUID ownerUserId
    ) {
        validate(snapshot);
        User owner = userRepository.findById(ownerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Nutzerkonto wurde nicht gefunden."));

        Project project = new Project();
        project.setTitle(snapshot.title().trim());
        project.setDescription(snapshot.description());
        project.setStartDate(snapshot.startDate());
        project.setEndDate(snapshot.endDate());
        project.setCategory(snapshot.category());
        project.setProjectType(snapshot.projectType());
        project.setCollaborationMode(snapshot.collaborationMode());
        project.setCreationType(CreationType.AI);
        project.setStatus(ProjectStatus.DRAFT);
        project.setLocation(ProjectLocation.DRAFT);

        ProjectMember membership = new ProjectMember();
        membership.setUser(owner);
        membership.setRole(ProjectMemberRole.OWNER);
        membership.setActive(true);
        project.addMembership(membership);
        projectRepository.save(project);

        AiPlanGenerationWorkflow workflow = AiPlanGenerationWorkflow.create(
                project,
                snapshotCodec.writeSnapshot(snapshot),
                SNAPSHOT_VERSION,
                completionToken,
                Instant.now(clock),
                CONSENT_VERSION
        );
        workflowRepository.saveAndFlush(workflow);
        completionTokenRepository.saveAndFlush(AiWorkflowCompletionToken.create(completionToken, workflow));
        eventPublisher.publishEvent(new AiPreCheckRequestedEvent(workflow.getId()));
        return new AiWorkflowCompletion(workflow.getId(), project.getId());
    }

    @Transactional
    public AiWorkflowCompletion restart(
            UUID workflowId,
            AiWizardSnapshot snapshot,
            UUID completionToken,
            UUID ownerUserId
    ) {
        validate(snapshot);
        AiPlanGenerationWorkflow workflow = workflowRepository.findOwnedById(workflowId, ownerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("KI-Workflow wurde nicht gefunden."));
        if (workflow.getStatus() != AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW) {
            throw new DomainValidationException("Nur ein Workflow mit aktuellen Hinweisen kann erneut geprüft werden.");
        }

        Project project = workflow.getProject();
        project.setTitle(snapshot.title().trim());
        project.setDescription(snapshot.description());
        project.setStartDate(snapshot.startDate());
        project.setEndDate(snapshot.endDate());
        project.setCategory(snapshot.category());
        project.setProjectType(snapshot.projectType());
        project.setCollaborationMode(snapshot.collaborationMode());

        workflow.restart(
                snapshotCodec.writeSnapshot(snapshot),
                completionToken,
                Instant.now(clock),
                CONSENT_VERSION);
        completionTokenRepository.saveAndFlush(AiWorkflowCompletionToken.create(completionToken, workflow));
        workflowRepository.flush();
        eventPublisher.publishEvent(new AiPreCheckRequestedEvent(workflow.getId()));
        return new AiWorkflowCompletion(workflow.getId(), project.getId());
    }

    private void validate(AiWizardSnapshot snapshot) {
        if (snapshot == null) {
            throw new DomainValidationException("Die bestätigten Projektdaten fehlen.");
        }
        if (snapshot.title() == null || snapshot.title().isBlank() || snapshot.title().length() > 100) {
            throw new DomainValidationException("Die bestätigten Projektdaten enthalten keinen gültigen Titel.");
        }
        if (snapshot.category() == null) {
            throw new DomainValidationException("Die bestätigten Projektdaten enthalten keine Kategorie.");
        }
        if (snapshot.collaborationMode() == null
                || snapshot.collaborationMode() == CollaborationMode.BOTH) {
            throw new DomainValidationException("Die bestätigten Projektdaten enthalten keine gültige Projektart.");
        }
        if (snapshot.startDate() != null && snapshot.endDate() != null
                && snapshot.endDate().isBefore(snapshot.startDate())) {
            throw new DomainValidationException("Das Projektende darf nicht vor dem Projektstart liegen.");
        }
    }
}
