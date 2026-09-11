package de.melinadanhier.projectflow.study.service;

import de.melinadanhier.projectflow.study.domain.*;
import de.melinadanhier.projectflow.study.repository.*;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudyTrackingService {
    public static final String SESSION_ATTRIBUTE = "studySessionId";
    public static final String PHASE_ATTRIBUTE = "studyPhase";
    public static final String PHASE_TASK_1 = "TASK_1";
    public static final String PHASE_TASK_2 = "TASK_2";
    private static final String TRACKED_GENERATIONS_ATTRIBUTE = "studyTrackedGenerationWorkflows";
    private final StudySessionRepository sessionRepository;
    private final StudyEventRepository eventRepository;
    private final Clock clock;

    @Transactional
    public UUID start(String participantId) {
        StudySession session = new StudySession();
        session.setParticipantId(participantId);
        session.setStartedAt(Instant.now(clock));
        return sessionRepository.save(session).getId();
    }

    @Transactional
    public void assignProjectIfActive(HttpSession httpSession, UUID projectId) {
        active(httpSession).filter(session -> session.getCompletedAt() == null).ifPresent(session -> {
            if (session.getProjectId() == null) session.setProjectId(projectId);
        });
    }

    @Transactional
    public void trackIfActive(HttpSession httpSession, StudyEventType type) {
        active(httpSession).filter(session -> session.getCompletedAt() == null)
                .ifPresent(session -> record(session, type));
    }

    @Transactional
    public void trackGeneratedPlanIfActive(HttpSession httpSession, UUID workflowId) {
        Set<UUID> tracked = trackedGenerations(httpSession);
        if (tracked.contains(workflowId)) return;
        active(httpSession).filter(session -> session.getCompletedAt() == null).ifPresent(session -> {
            record(session, StudyEventType.PLAN_GENERATED);
            tracked.add(workflowId);
        });
    }

    @Transactional
    public boolean finish(HttpSession httpSession) {
        Optional<StudySession> active = active(httpSession);
        active.filter(session -> session.getCompletedAt() == null)
                .ifPresent(session -> session.setCompletedAt(Instant.now(clock)));
        httpSession.removeAttribute(SESSION_ATTRIBUTE);
        httpSession.removeAttribute(PHASE_ATTRIBUTE);
        httpSession.removeAttribute(TRACKED_GENERATIONS_ATTRIBUTE);
        return active.isPresent();
    }

    private Optional<StudySession> active(HttpSession session) {
        Object value = session.getAttribute(SESSION_ATTRIBUTE);
        return value instanceof UUID id ? sessionRepository.findById(id) : Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<String> activeParticipantId(HttpSession httpSession) {
        return active(httpSession)
                .filter(session -> session.getCompletedAt() == null)
                .map(StudySession::getParticipantId);
    }

    @Transactional(readOnly = true)
    public Optional<UUID> activeProjectId(HttpSession httpSession) {
        return active(httpSession)
                .filter(session -> session.getCompletedAt() == null)
                .map(StudySession::getProjectId);
    }

    public boolean isActive(HttpSession httpSession) {
        return active(httpSession)
                .filter(session -> session.getCompletedAt() == null)
                .isPresent();
    }

    private void record(StudySession session, StudyEventType type) {
        StudyEvent event = new StudyEvent();
        event.setStudySession(session);
        event.setEventType(type);
        event.setOccurredAt(Instant.now(clock));
        eventRepository.save(event);
    }

    @SuppressWarnings("unchecked")
    private Set<UUID> trackedGenerations(HttpSession session) {
        Object value = session.getAttribute(TRACKED_GENERATIONS_ATTRIBUTE);
        if (value instanceof Set<?>) return (Set<UUID>) value;
        Set<UUID> created = new HashSet<>();
        session.setAttribute(TRACKED_GENERATIONS_ATTRIBUTE, created);
        return created;
    }
}
