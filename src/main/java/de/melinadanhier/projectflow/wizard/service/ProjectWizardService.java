package de.melinadanhier.projectflow.wizard.service;

import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectCreateForm;
import de.melinadanhier.projectflow.plancontainer.project.model.CreationType;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import de.melinadanhier.projectflow.wizard.dto.ProjectBasicsForm;
import de.melinadanhier.projectflow.wizard.model.ProjectWizardState;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectWizardService {

    public static final String SESSION_ATTRIBUTE = ProjectWizardState.class.getName();

    private final ProjectTimeFrameCalculator timeFrameCalculator;

    public ProjectWizardState saveBasics(ProjectBasicsForm form, UUID userId, HttpSession session) {
        ProjectWizardState state = findOwned(userId, session).orElseGet(ProjectWizardState::new);
        state.setUserId(userId);
        state.setTitle(form.getTitle().trim());
        state.setDescription(normalizeOptionalText(form.getDescription()));
        state.setCategory(form.getCategory());
        state.setProjectType(normalizeOptionalText(form.getCategory() == TemplateCategory.OTHER
                ? form.getOtherProjectTypeDescription() : form.getSubcategory()));
        state.setCollaborationMode(form.getCollaborationMode());
        state.setTimeFrameType(form.getTimeFrameType());
        state.setDurationDays(form.getDurationDays());
        ProjectTimeFrameCalculator.ProjectTimeFrame timeFrame = timeFrameCalculator.calculate(form);
        state.setStartDate(timeFrame.startDate());
        state.setEndDate(timeFrame.endDate());
        session.setAttribute(SESSION_ATTRIBUTE, state);
        return state;
    }

    public ProjectWizardState selectCreationType(
            CreationType creationType, UUID userId, HttpSession session) {
        ProjectWizardState state = requireOwned(userId, session);
        state.setCreationType(creationType);
        session.setAttribute(SESSION_ATTRIBUTE, state);
        return state;
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

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
