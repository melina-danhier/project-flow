package de.melinadanhier.projectflow.plancontainer.project.service;

import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectCreateForm;
import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectCreationFlowState;
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
}
