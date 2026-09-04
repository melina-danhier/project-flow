package de.melinadanhier.projectflow.wizard.service;

import de.melinadanhier.projectflow.plancontainer.project.validation.ProjectClassificationValidator;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.model.wizard.AiProjectTimeFrameType;
import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectCreateForm;
import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import de.melinadanhier.projectflow.wizard.dto.AiProjectDetailsForm;
import de.melinadanhier.projectflow.wizard.dto.AiWizardSummary;
import de.melinadanhier.projectflow.wizard.dto.ProjectBasicsForm;
import de.melinadanhier.projectflow.wizard.dto.ProjectTimeFrameType;
import de.melinadanhier.projectflow.wizard.model.ProjectWizardState;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectWizardService {

    public static final String SESSION_ATTRIBUTE = ProjectWizardState.class.getName();

    private final ProjectTimeFrameCalculator timeFrameCalculator;

    public ProjectWizardState saveBasics(ProjectBasicsForm form, UUID userId, HttpSession session) {
        ProjectClassificationValidator.requireValid(form.getCategory(), form.getSubcategory(),
                form.isOtherCategory() && (form.getOtherProjectTypeDescription() == null
                        || form.getOtherProjectTypeDescription().isBlank())
                        ? "Sonstiges Projekt" : form.getOtherProjectTypeDescription());
        ProjectWizardState state = findOwned(userId, session).orElseGet(ProjectWizardState::new);
        boolean classificationChanged = state.getCategory() != form.getCategory()
                || state.getSubcategory() != form.getSubcategory();
        state.setUserId(userId);
        state.setTitle(form.getTitle().trim());
        state.setDescription(normalizeOptionalText(form.getDescription()));
        state.setCategory(form.getCategory());
        state.setSubcategory(form.getSubcategory());
        state.setOtherProjectTypeDescription(form.isOtherCategory()
                ? normalizeOptionalText(form.getOtherProjectTypeDescription()) : null);
        state.setCollaborationMode(form.getCollaborationMode());
        state.setTimeFrameType(form.getTimeFrameType());
        state.setDurationDays(form.getDurationDays());
        ProjectTimeFrameCalculator.ProjectTimeFrame timeFrame = timeFrameCalculator.calculate(form);
        state.setStartDate(timeFrame.startDate());
        state.setEndDate(timeFrame.endDate());
        state.setCompletionToken(null);
        if (classificationChanged) {
            state.getProjectSpecificAnswers().clear();
            state.setAiDetailsCompleted(false);
        }
        session.setAttribute(SESSION_ATTRIBUTE, state);
        return state;
    }

    public ProjectWizardState selectCreationType(
            CreationType creationType, UUID userId, HttpSession session) {
        ProjectWizardState state = requireOwned(userId, session);
        state.setCreationType(creationType);
        state.setCompletionToken(null);
        session.setAttribute(SESSION_ATTRIBUTE, state);
        return state;
    }

    public ProjectWizardState saveAiDetails(
            AiProjectDetailsForm form, UUID userId, HttpSession session) {
        ProjectWizardState state = requireOwnedFor(CreationType.AI, userId, session);
        state.setProjectSpecificAnswers(new java.util.LinkedHashMap<>(
                AiProjectQuestionCatalog.sanitize(state.getCategory(), state.getSubcategory(), form.getAnswers())));
        state.setAiDetailsCompleted(true);
        state.setCompletionToken(null);
        session.setAttribute(SESSION_ATTRIBUTE, state);
        return state;
    }

    public AiWizardSummary aiSummary(UUID userId, HttpSession session) {
        ProjectWizardState state = requireOwnedFor(CreationType.AI, userId, session);
        if (!state.isAiDetailsCompleted()) {
            throw new ResourceNotFoundException("Die KI-Angaben wurden noch nicht abgeschlossen.");
        }
        List<AiWizardSummary.Answer> answers = AiProjectQuestionCatalog
                .questionsFor(state.getCategory(), state.getSubcategory()).stream()
                .filter(question -> state.getProjectSpecificAnswers().containsKey(question.key()))
                .map(question -> new AiWizardSummary.Answer(question.key(), question.label(),
                        state.getProjectSpecificAnswers().get(question.key())))
                .toList();
        return new AiWizardSummary(
                state.getTitle(), state.getDescription(), state.getStartDate(), state.getEndDate(),
                state.getCollaborationMode() == CollaborationMode.GROUP,
                categoryLabel(state.getCategory(), state.getProjectTypeLabel()),
                "KI-generierter Plan",
                state.getProjectGoal(), state.getConstraints(), state.getAdditionalInformation(), answers);
    }

    public UUID completionToken(UUID userId, HttpSession session) {
        ProjectWizardState state = requireOwnedFor(CreationType.AI, userId, session);
        if (!state.isAiDetailsCompleted()) {
            throw new ResourceNotFoundException("Die KI-Angaben wurden noch nicht abgeschlossen.");
        }
        if (state.getCompletionToken() == null) {
            state.setCompletionToken(UUID.randomUUID());
        }
        session.setAttribute(SESSION_ATTRIBUTE, state);
        return state.getCompletionToken();
    }

    public AiWizardSnapshot confirmedSnapshot(
            UUID completionToken, UUID userId, HttpSession session) {
        ProjectWizardState state = requireOwnedFor(CreationType.AI, userId, session);
        if (!state.isAiDetailsCompleted() || !completionToken.equals(state.getCompletionToken())) {
            throw new ConflictException(
                    "Diese Zusammenfassung ist nicht mehr aktuell. Bitte prüfe deine Angaben erneut.");
        }
        return new AiWizardSnapshot(
                state.getTitle(), state.getDescription(), state.getStartDate(), state.getEndDate(),
                state.getCollaborationMode(), state.getCategory(), state.getSubcategory(), state.getOtherProjectTypeDescription(),
                state.getProjectGoal(), state.getConstraints(), state.getAdditionalInformation(),
                AiProjectTimeFrameType.valueOf(state.getTimeFrameType().name()), state.getDurationDays(),
                state.getProjectSpecificAnswers());
    }

    public ProjectWizardState requireOwned(UUID userId, HttpSession session) {
        return findOwned(userId, session)
                .orElseThrow(() -> new ResourceNotFoundException("Erstellungsablauf wurde nicht gefunden."));
    }

    public ProjectWizardState requireOwnedFor(
            CreationType creationType, UUID userId, HttpSession session) {
        ProjectWizardState state = requireOwned(userId, session);
        if (state.getCreationType() != creationType) {
            throw new ResourceNotFoundException("Erstellungsablauf wurde nicht gefunden.");
        }
        return state;
    }

    public Optional<ProjectWizardState> findOwned(UUID userId, HttpSession session) {
        Object value = session.getAttribute(SESSION_ATTRIBUTE);
        if (value instanceof ProjectWizardState state && userId.equals(state.getUserId())) {
            return Optional.of(state);
        }
        return Optional.empty();
    }

    public ProjectCreateForm projectData(UUID userId, HttpSession session) {
        return requireOwned(userId, session).toProjectCreateForm();
    }

    public void clearOwned(UUID userId, HttpSession session) {
        if (findOwned(userId, session).isPresent()) {
            session.removeAttribute(SESSION_ATTRIBUTE);
        }
    }

    public ProjectWizardState restoreFromSnapshot(
            AiWizardSnapshot snapshot,
            UUID userId,
            HttpSession session
    ) {
        ProjectWizardState state = new ProjectWizardState();
        state.setUserId(userId);
        state.setTitle(snapshot.title());
        state.setDescription(snapshot.description());
        state.setCategory(snapshot.category());
        state.setOtherProjectTypeDescription(snapshot.otherProjectTypeDescription());
        state.setSubcategory(snapshot.subcategory());
        state.setCollaborationMode(snapshot.collaborationMode());
        state.setCreationType(CreationType.AI);
        state.setStartDate(snapshot.startDate());
        state.setEndDate(snapshot.endDate());
        state.setTimeFrameType(resolveTimeFrameType(snapshot));
        state.setDurationDays(snapshot.durationDays());
        state.setProjectGoal(snapshot.projectGoal());
        state.setConstraints(snapshot.constraints());
        state.setAdditionalInformation(snapshot.additionalInformation());
        state.setProjectSpecificAnswers(new java.util.LinkedHashMap<>(snapshot.projectSpecificAnswers()));
        state.setAiDetailsCompleted(true);
        state.setCompletionToken(null);
        session.setAttribute(SESSION_ATTRIBUTE, state);
        return state;
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ProjectTimeFrameType resolveTimeFrameType(AiWizardSnapshot snapshot) {
        if (snapshot.timeFrameType() != null) {
            return ProjectTimeFrameType.valueOf(snapshot.timeFrameType().name());
        }
        return snapshot.startDate() == null && snapshot.endDate() == null
                ? ProjectTimeFrameType.NONE
                : ProjectTimeFrameType.START_AND_END;
    }

    private String categoryLabel(TemplateCategory category, String projectTypeLabel) {
        String label = switch (category) {
            case EDUCATION -> "Bildung und Studium";
            case SOFTWARE_TECHNOLOGY -> "Software und Technik";
            case EVENT -> "Veranstaltung";
            case HOME -> "Haushalt und Wohnen";
            case CREATIVE -> "Kreatives";
            case CAREER -> "Beruf und Karriere";
            case HEALTH_PERSONAL_DEVELOPMENT -> "Gesundheit und persönliche Entwicklung";
            case TRAVEL -> "Reise";
            case OTHER -> "Sonstiges";
        };
        return projectTypeLabel == null ? label : label + " – " + projectTypeLabel;
    }
}
