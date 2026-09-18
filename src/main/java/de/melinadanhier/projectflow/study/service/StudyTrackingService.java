package de.melinadanhier.projectflow.study.service;

import de.melinadanhier.projectflow.study.domain.StudyEvent;
import de.melinadanhier.projectflow.study.domain.StudyEventType;
import de.melinadanhier.projectflow.study.domain.StudyPhase;
import de.melinadanhier.projectflow.study.domain.StudySession;
import de.melinadanhier.projectflow.study.domain.StudySessionStatus;
import de.melinadanhier.projectflow.study.repository.StudyEventRepository;
import de.melinadanhier.projectflow.study.repository.StudySessionRepository;
import de.melinadanhier.projectflow.plancontainer.project.model.lifecycle.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class StudyTrackingService {

    public static final String SESSION_ATTRIBUTE = "studySessionId";
    public static final String PHASE_ATTRIBUTE = "studyPhase";
    public static final String TASKS_COMPLETED_ATTRIBUTE = "studyTasksCompleted";
    public static final String SHOW_INTRO_ATTRIBUTE = "showStudyTaskIntro";
    public static final String TASK_TWO_CAN_COMPLETE_ATTRIBUTE = "studyTaskTwoCanComplete";

    public static final Set<StudyEventType> TASK_TWO_COMPLETION_EVENTS = Set.of(
            StudyEventType.LOCAL_AI_CHANGE_ADOPTED,
            StudyEventType.LOCAL_AI_CHANGE_REJECTED,
            StudyEventType.PLAN_AI_CHANGE_ADOPTED,
            StudyEventType.PLAN_AI_CHANGE_REJECTED,
            StudyEventType.AI_EDIT_ADOPTED,
            StudyEventType.AI_EDIT_REJECTED
    );

    private static final String TRACKED_GENERATIONS_ATTRIBUTE =
            "studyTrackedGenerationWorkflows";

    private final StudySessionRepository sessionRepository;
    private final StudyEventRepository eventRepository;
    private final ProjectRepository projectRepository;
    private final Clock clock;

    @Autowired
    public StudyTrackingService(StudySessionRepository sessionRepository,
                                StudyEventRepository eventRepository,
                                ProjectRepository projectRepository,
                                Clock clock) {
        this.sessionRepository = sessionRepository;
        this.eventRepository = eventRepository;
        this.projectRepository = projectRepository;
        this.clock = clock;
    }

    public StudyTrackingService(StudySessionRepository sessionRepository,
                                StudyEventRepository eventRepository,
                                Clock clock) {
        this(sessionRepository, eventRepository, null, clock);
    }

    @Transactional
    public UUID start(HttpSession httpSession) {
        if (activeSession(httpSession).isPresent()) {
            throw new IllegalStateException("Es ist bereits eine Studien-Session aktiv.");
        }
        StudySession studySession = createStudySession();
        bindToHttpSession(httpSession, studySession);
        recordEvent(studySession, StudyEventType.STUDY_STARTED);
        return studySession.getId();
    }

    @Transactional
    public boolean finish(HttpSession httpSession) {
        Optional<StudySession> studySession = activeSession(httpSession);

        studySession.ifPresent(session -> {
            boolean alreadyLoggedTaskTwo = httpSession != null && Boolean.TRUE.equals(httpSession.getAttribute(TASKS_COMPLETED_ATTRIBUTE));
            if (!alreadyLoggedTaskTwo) {
                recordEvent(session, StudyEventType.STUDY_TASK_COMPLETED);
            }
            recordEvent(session, StudyEventType.STUDY_COMPLETED);
            session.setCompletedAt(Instant.now(clock));
            session.setStatus(StudySessionStatus.COMPLETED);
        });

        clearHttpSession(httpSession);

        return studySession.isPresent();
    }

    @Transactional
    public boolean abort(HttpSession httpSession) {
        Optional<StudySession> studySession = activeSession(httpSession);
        studySession.ifPresent(session -> {
            recordEvent(session, StudyEventType.STUDY_ABORTED);
            session.setStatus(StudySessionStatus.ABORTED);
        });
        clearHttpSession(httpSession);
        return studySession.isPresent();
    }

    @Transactional
    public void completeCurrentTask(HttpSession httpSession) {
        recordEvent(requireActiveSession(httpSession), StudyEventType.STUDY_TASK_COMPLETED);
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
    public UUID completeTaskOne(HttpSession httpSession) {
        StudySession studySession = requireActiveSession(httpSession);

        if (studySession.getCurrentPhase() != StudyPhase.TASK_1) {
            throw new IllegalStateException("Aufgabe 1 ist nicht die aktive Phase.");
        }
        if (studySession.getProjectId() == null) {
            throw new IllegalStateException("Kein Studienprojekt vorhanden.");
        }
        boolean hasManageablePlan = projectRepository == null || projectRepository.findById(studySession.getProjectId())
                .filter(project -> project.getLocation() != ProjectLocation.DRAFT)
                .isPresent();
        if (!hasManageablePlan) {
            throw new IllegalStateException("Aufgabe 1 kann erst abgeschlossen werden, wenn ein Plan übernommen wurde.");
        }

        recordEvent(studySession, StudyEventType.STUDY_TASK_COMPLETED);
        setPhase(httpSession, studySession, StudyPhase.TASK_2);
        return studySession.getProjectId();
    }

    @Transactional
    public void completeTaskTwo(HttpSession httpSession) {
        StudySession studySession = requireActiveSession(httpSession);

        if (studySession.getCurrentPhase() != StudyPhase.TASK_2) {
            throw new IllegalStateException("Aufgabe 2 ist nicht die aktive Phase.");
        }
        if (!canCompleteTaskTwo(httpSession)) {
            throw new IllegalStateException("Aufgabe 2 kann erst abgeschlossen werden, wenn mindestens eine KI-Änderung vorgenommen wurde.");
        }

        recordEvent(studySession, StudyEventType.STUDY_TASK_COMPLETED);
        if (httpSession != null) {
            httpSession.setAttribute(TASKS_COMPLETED_ATTRIBUTE, true);
        }
    }

    @Transactional(readOnly = true)
    public boolean canCompleteTaskTwo(HttpSession httpSession) {
        if (httpSession != null && Boolean.TRUE.equals(httpSession.getAttribute(TASK_TWO_CAN_COMPLETE_ATTRIBUTE))) {
            return true;
        }

        return activeSession(httpSession)
                .filter(session -> session.getCurrentPhase() == StudyPhase.TASK_2)
                .map(session -> {
                    boolean canComplete = eventRepository != null && eventRepository.existsByStudySessionAndStudyPhaseAndEventTypeIn(
                            session, StudyPhase.TASK_2, TASK_TWO_COMPLETION_EVENTS);
                    if (canComplete && httpSession != null) {
                        httpSession.setAttribute(TASK_TWO_CAN_COMPLETE_ATTRIBUTE, true);
                    }
                    return canComplete;
                })
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canCompleteTaskOne(HttpSession httpSession) {
        return activeSession(httpSession)
                .filter(session -> session.getCurrentPhase() == StudyPhase.TASK_1)
                .filter(session -> session.getProjectId() != null)
                .map(session -> projectRepository == null || projectRepository.findById(session.getProjectId())
                        .filter(project -> project.getLocation() != ProjectLocation.DRAFT)
                        .isPresent())
                .orElse(false);
    }

    @Transactional
    public void assignProjectIfActive(HttpSession httpSession, UUID projectId) {
        activeSession(httpSession)
                .filter(session -> session.getProjectId() == null || (projectRepository != null && !projectRepository.existsById(session.getProjectId())))
                .ifPresent(session -> session.setProjectId(projectId));
    }

    @Transactional
    public void trackIfActive(HttpSession httpSession, StudyEventType type) {
        activeSession(httpSession)
                .ifPresent(session -> {
                    recordEvent(session, type);
                    if (session.getCurrentPhase() == StudyPhase.TASK_2 && TASK_TWO_COMPLETION_EVENTS.contains(type)) {
                        if (httpSession != null) {
                            httpSession.setAttribute(TASK_TWO_CAN_COMPLETE_ATTRIBUTE, true);
                        }
                    }
                });
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
    public Optional<UUID> activeProjectId(HttpSession httpSession) {
        return activeSession(httpSession)
                .map(StudySession::getProjectId);
    }

    @Transactional(readOnly = true)
    public boolean isActive(HttpSession httpSession) {
        return activeSession(httpSession).isPresent();
    }

    private StudySession createStudySession() {
        StudySession studySession = new StudySession();
        Instant now = Instant.now(clock);
        studySession.setConsentGivenAt(now);
        studySession.setStartedAt(now);
        studySession.setStatus(StudySessionStatus.ACTIVE);
        studySession.setCurrentPhase(StudyPhase.TASK_1);

        return sessionRepository.save(studySession);
    }

    private Optional<StudySession> activeSession(HttpSession httpSession) {
        return findSession(httpSession)
                .filter(session -> session.getStatus() == StudySessionStatus.ACTIVE);
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
        httpSession.removeAttribute(TASKS_COMPLETED_ATTRIBUTE);
        httpSession.removeAttribute(SHOW_INTRO_ATTRIBUTE);
        httpSession.removeAttribute(TASK_TWO_CAN_COMPLETE_ATTRIBUTE);
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
        if (httpSession == null) {
            return;
        }

        StudyPhase phase = studySession.getCurrentPhase();

        if (phase == null) {
            httpSession.removeAttribute(PHASE_ATTRIBUTE);
            httpSession.removeAttribute(TASK_TWO_CAN_COMPLETE_ATTRIBUTE);
            return;
        }

        httpSession.setAttribute(PHASE_ATTRIBUTE, phase.name());
        if (phase == StudyPhase.TASK_2) {
            boolean canComplete = eventRepository != null && eventRepository.existsByStudySessionAndStudyPhaseAndEventTypeIn(
                    studySession, StudyPhase.TASK_2, TASK_TWO_COMPLETION_EVENTS);
            httpSession.setAttribute(TASK_TWO_CAN_COMPLETE_ATTRIBUTE, canComplete);
        } else {
            httpSession.removeAttribute(TASK_TWO_CAN_COMPLETE_ATTRIBUTE);
        }
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
