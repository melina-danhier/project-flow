package de.melinadanhier.projectflow.generation.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class AiPreCheckReviewSession {

    private static final String ATTRIBUTE = AiPreCheckReviewSession.class.getName() + ".state";

    public Set<Integer> ignoredWarnings(UUID workflowId, HttpSession session) {
        return Set.copyOf(state(session).ignoredWarningIndicesByWorkflow
                .getOrDefault(workflowId, Set.of()));
    }

    public Set<Integer> ignore(UUID workflowId, int problemIndex, HttpSession session) {
        State state = state(session);
        state.ignoredWarningIndicesByWorkflow
                .computeIfAbsent(workflowId, ignored -> new HashSet<>())
                .add(problemIndex);
        session.setAttribute(ATTRIBUTE, state);
        return Set.copyOf(state.ignoredWarningIndicesByWorkflow.get(workflowId));
    }

    public void clear(UUID workflowId, HttpSession session) {
        Object value = session.getAttribute(ATTRIBUTE);
        if (value instanceof State state) {
            state.ignoredWarningIndicesByWorkflow.remove(workflowId);
            if (state.ignoredWarningIndicesByWorkflow.isEmpty()) {
                session.removeAttribute(ATTRIBUTE);
            } else {
                session.setAttribute(ATTRIBUTE, state);
            }
        }
    }

    private State state(HttpSession session) {
        Object value = session.getAttribute(ATTRIBUTE);
        if (value instanceof State state) {
            return state;
        }
        State state = new State();
        session.setAttribute(ATTRIBUTE, state);
        return state;
    }

    private static final class State implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final Map<UUID, Set<Integer>> ignoredWarningIndicesByWorkflow = new HashMap<>();
    }
}
