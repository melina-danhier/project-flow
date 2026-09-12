package de.melinadanhier.projectflow.study.service;

import de.melinadanhier.projectflow.study.domain.StudyEvent;
import de.melinadanhier.projectflow.study.domain.StudyEventType;
import de.melinadanhier.projectflow.study.domain.StudyPhase;
import de.melinadanhier.projectflow.study.domain.StudySession;
import de.melinadanhier.projectflow.study.repository.StudyEventRepository;
import de.melinadanhier.projectflow.study.repository.StudySessionRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudyTrackingService {

    public static final String SESSION_ATTRIBUTE = "studySessionId";
    public static final String PHASE_ATTRIBUTE = "studyPhase";

    private static final String TRACKED_GENERATIONS_ATTRIBUTE =
            "studyTrackedGenerationWorkflows";

    private final StudySessionRepository sessionRepository;
    private final StudyEventRepository eventRepository;
    private final Clock clock;

    @Transactional
    public void startOrResume(HttpSession httpSession, String participantId) {
        StudySession studySession = sessionRepository
                .findFirstByParticipantIdAndCompletedAtIsNullOrderByStartedAtDesc(participantId)
                .orElseGet(() -> createStudySession(participantId));

        bindToHttpSession(httpSession, studySession);
    }

    @Transactional
    public void resume(HttpSession httpSession, String participantId) {
        StudySession studySession = sessionRepository
                .findFirstByParticipantIdAndCompletedAtIsNullOrderByStartedAtDesc(participantId)
                .orElseThrow(() -> new IllegalStateException(
                        "Keine aktive Studien-Session für diesen Teilnehmer vorhanden."
                ));

        bindToHttpSession(httpSession, studySession);
    }

    @Transactional
    public boolean finish(HttpSession httpSession) {
        Optional<StudySession> studySession = activeSession(httpSession);

        studySession.ifPresent(session ->
                session.setCompletedAt(Instant.now(clock))
        );

        clearHttpSession(httpSession);

        return studySession.isPresent();
    }

    @Transactional
    public void beginTaskOne(HttpSession httpSession) {
        StudySession studySession = requireActiveSession(httpSession);

        if (studySession.getCurrentPhase() == null) {
            setPhase(httpSession, studySession, StudyPhase.TASK_1);
        }
    }

    @Transactional
    public void beginTaskTwo(HttpSession httpSession) {
        StudySession studySession = requireActiveSession(httpSession);

        if (studySession.getProjectId() == null) {
            throw new IllegalStateException("Kein Studienprojekt vorhanden.");
        }

        setPhase(httpSession, studySession, StudyPhase.TASK_2);
    }

    @Transactional
    public void assignProjectIfActive(HttpSession httpSession, UUID projectId) {
        activeSession(httpSession)
                .filter(session -> session.getProjectId() == null)
                .ifPresent(session -> session.setProjectId(projectId));
    }

    @Transactional
    public void trackIfActive(HttpSession httpSession, StudyEventType type) {
        activeSession(httpSession)
                .ifPresent(session -> recordEvent(session, type));
    }

    @Transactional
    public void trackGeneratedPlanIfActive(HttpSession httpSession, UUID workflowId) {
        Set<UUID> trackedGenerations = trackedGenerations(httpSession);

        if (trackedGenerations.contains(workflowId)) {
            return;
        }

        activeSession(httpSession).ifPresent(session -> {
            recordEvent(session, StudyEventType.PLAN_GENERATED);
            trackedGenerations.add(workflowId);
        });
    }

    @Transactional(readOnly = true)
    public Optional<String> activeParticipantId(HttpSession httpSession) {
        return activeSession(httpSession)
                .map(StudySession::getParticipantId);
    }

    @Transactional(readOnly = true)
    public Optional<UUID> activeProjectId(HttpSession httpSession) {
        return activeSession(httpSession)
                .map(StudySession::getProjectId);
    }

    @Transactional(readOnly = true)
    public boolean isActive(HttpSession httpSession) {
        return activeSession(httpSession).isPresent();
    }

    private StudySession createStudySession(String participantId) {
        StudySession studySession = new StudySession();
        studySession.setParticipantId(participantId);
        studySession.setStartedAt(Instant.now(clock));

        return sessionRepository.save(studySession);
    }

    private Optional<StudySession> activeSession(HttpSession httpSession) {
        return findSession(httpSession)
                .filter(session -> session.getCompletedAt() == null);
    }

    private Optional<StudySession> findSession(HttpSession httpSession) {
        Object value = httpSession.getAttribute(SESSION_ATTRIBUTE);

        if (!(value instanceof UUID sessionId)) {
            return Optional.empty();
        }

        return sessionRepository.findById(sessionId);
    }

    private StudySession requireActiveSession(HttpSession httpSession) {
        return activeSession(httpSession)
                .orElseThrow(() ->
                        new IllegalStateException("Keine aktive Studien-Session vorhanden.")
                );
    }

    private void bindToHttpSession(
            HttpSession httpSession,
            StudySession studySession
    ) {
        httpSession.setAttribute(SESSION_ATTRIBUTE, studySession.getId());
        synchronizePhase(httpSession, studySession);
    }

    private void clearHttpSession(HttpSession httpSession) {
        httpSession.removeAttribute(SESSION_ATTRIBUTE);
        httpSession.removeAttribute(PHASE_ATTRIBUTE);
        httpSession.removeAttribute(TRACKED_GENERATIONS_ATTRIBUTE);
    }

    private void setPhase(
            HttpSession httpSession,
            StudySession studySession,
            StudyPhase phase
    ) {
        studySession.setCurrentPhase(phase);
        synchronizePhase(httpSession, studySession);
    }

    private void synchronizePhase(
            HttpSession httpSession,
            StudySession studySession
    ) {
        StudyPhase phase = studySession.getCurrentPhase();

        if (phase == null) {
            httpSession.removeAttribute(PHASE_ATTRIBUTE);
            return;
        }

        httpSession.setAttribute(PHASE_ATTRIBUTE, phase.name());
    }

    private void recordEvent(
            StudySession studySession,
            StudyEventType type
    ) {
        StudyPhase phase = studySession.getCurrentPhase();

        if (phase == null) {
            throw new IllegalStateException(
                    "Für das Studienereignis ist keine Aufgabenphase gesetzt."
            );
        }

        StudyEvent event = new StudyEvent();
        event.setStudySession(studySession);
        event.setEventType(type);
        event.setStudyPhase(phase);
        event.setOccurredAt(Instant.now(clock));

        eventRepository.save(event);
    }

    @SuppressWarnings("unchecked")
    private Set<UUID> trackedGenerations(HttpSession httpSession) {
        Object value = httpSession.getAttribute(TRACKED_GENERATIONS_ATTRIBUTE);

        if (value instanceof Set<?>) {
            return (Set<UUID>) value;
        }

        Set<UUID> tracked = new HashSet<>();
        httpSession.setAttribute(TRACKED_GENERATIONS_ATTRIBUTE, tracked);

        return tracked;
    }
}