package de.melinadanhier.projectflow.plancontainer.project.service;

import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectCreateForm;
import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectBasicsForm;
import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectCreationFlowState;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class ProjectCreationFlowService {

    public static final String SESSION_ATTRIBUTE = ProjectCreationFlowState.class.getName();

    public ProjectCreationFlowState store(ProjectCreateForm form, UUID userId, HttpSession session) {
        ProjectCreationFlowState state = ProjectCreationFlowState.from(form, userId);
        session.setAttribute(SESSION_ATTRIBUTE, state);
        return state;
    }

    public ProjectCreationFlowState requireOwned(UUID userId, HttpSession session) {
        return findOwned(userId, session)
                .orElseThrow(() -> new ResourceNotFoundException("Erstellungsablauf wurde nicht gefunden."));
    }

    public ProjectCreationFlowState updateBasics(
            ProjectBasicsForm form,
            UUID userId,
            HttpSession session
    ) {
        ProjectCreationFlowState state = requireOwned(userId, session);
        state.setTitle(form.getTitle().trim());
        state.setCategory(form.getCategory());
        state.setProjectType(normalizeOptionalText(form.getCategory() == TemplateCategory.OTHER
                ? form.getOtherProjectTypeDescription()
                : form.getSubcategory()));
        state.setTimeFrameType(form.getTimeFrameType());
        state.setDurationDays(form.getDurationDays());
        applyAbsoluteDates(state, form);
        session.setAttribute(SESSION_ATTRIBUTE, state);
        return state;
    }

    public Optional<ProjectCreationFlowState> findOwned(UUID userId, HttpSession session) {
        Object value = session.getAttribute(SESSION_ATTRIBUTE);
        if (value instanceof ProjectCreationFlowState state && userId.equals(state.getUserId())) {
            return Optional.of(state);
        }
        return Optional.empty();
    }

    public void clearOwned(UUID userId, HttpSession session) {
        if (findOwned(userId, session).isPresent()) {
            session.removeAttribute(SESSION_ATTRIBUTE);
        }
    }

    private void applyAbsoluteDates(ProjectCreationFlowState state, ProjectBasicsForm form) {
        switch (form.getTimeFrameType()) {
            case START_AND_END -> {
                state.setStartDate(form.getStartDate());
                state.setEndDate(form.getEndDate());
            }
            case START_AND_DURATION -> {
                state.setStartDate(form.getStartDate());
                state.setEndDate(form.getStartDate().plusDays(form.getDurationDays() - 1L));
            }
            case END_AND_DURATION -> {
                state.setStartDate(form.getEndDate().minusDays(form.getDurationDays() - 1L));
                state.setEndDate(form.getEndDate());
            }
            case NONE -> {
                state.setStartDate(null);
                state.setEndDate(null);
            }
        }
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
