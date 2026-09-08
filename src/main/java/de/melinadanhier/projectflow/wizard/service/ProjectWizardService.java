package de.melinadanhier.projectflow.wizard.service;

import de.melinadanhier.projectflow.plancontainer.project.validation.ProjectClassificationValidator;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.plancontainer.project.dto.form.ProjectCreateForm;
import de.melinadanhier.projectflow.plancontainer.project.model.lifecycle.CreationType;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.wizard.dto.AiProjectDetailsForm;
import de.melinadanhier.projectflow.wizard.dto.AiWizardSummary;
import de.melinadanhier.projectflow.wizard.dto.ProjectBasicsForm;
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
        state.setStartDate(form.getStartDate());
        state.setEndDate(form.getEndDate());
        state.setDurationDays(form.getDurationDays());
        state.setAvailableWorkingTime(normalizeOptionalText(form.getAvailableWorkingTime()));
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
        if (creationType != CreationType.TEMPLATE) {
            state.setSelectedTemplateId(null);
        }
        state.setCompletionToken(null);
        session.setAttribute(SESSION_ATTRIBUTE, state);
        return state;
    }

    public ProjectWizardState startWithTemplate(UUID templateId, UUID userId, HttpSession session) {
        ProjectWizardState state = findOwned(userId, session).orElseGet(ProjectWizardState::new);
        state.setUserId(userId);
        state.setCreationType(CreationType.TEMPLATE);
        state.setSelectedTemplateId(templateId);
        session.setAttribute(SESSION_ATTRIBUTE, state);
        return state;
    }

    public ProjectWizardState selectTemplate(UUID templateId, UUID userId, HttpSession session) {
        ProjectWizardState state = requireOwnedFor(CreationType.TEMPLATE, userId, session);
        state.setSelectedTemplateId(templateId);
        session.setAttribute(SESSION_ATTRIBUTE, state);
        return state;
    }

    public ProjectWizardState saveAiDetails(
            AiProjectDetailsForm form, UUID userId, HttpSession session) {
        ProjectWizardState state = requireOwnedFor(CreationType.AI, userId, session);
        state.setProjectSpecificAnswers(new java.util.LinkedHashMap<>(
                AiProjectQuestionCatalog.sanitize(state.getCategory(), state.getSubcategory(), form.getAnswers())));
        state.setAdditionalInformation(normalizeOptionalText(form.getAdditionalInformation()));
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
                categoryLabel(state),
                "KI-generierter Plan",
                state.getDurationDays(), state.getAvailableWorkingTime(),
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
                state.getDurationDays(), state.getAvailableWorkingTime(),
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
        state.setDurationDays(snapshot.durationDays());
        state.setAvailableWorkingTime(snapshot.availableWorkingTime());
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

    private String categoryLabel(ProjectWizardState state) {
        String displayCategory = state.getDisplayCategory();
        return state.getSubcategory() != null && !state.getSubcategory().isOther()
                ? state.getCategory().getLabel() + " – " + displayCategory
                : displayCategory;
    }

}
