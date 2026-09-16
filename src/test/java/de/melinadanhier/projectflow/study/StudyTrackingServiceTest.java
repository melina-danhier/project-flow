package de.melinadanhier.projectflow.study;

import de.melinadanhier.projectflow.study.domain.*;
import de.melinadanhier.projectflow.study.repository.*;
import de.melinadanhier.projectflow.study.service.StudyTrackingService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.time.*;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StudyTrackingServiceTest {
    private final StudySessionRepository sessions = mock(StudySessionRepository.class);
    private final StudyEventRepository events = mock(StudyEventRepository.class);
    private final Instant now = Instant.parse("2026-09-10T12:00:00Z");
    private final StudyTrackingService service = new StudyTrackingService(
            sessions, events, Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void tracksOnlyWhenSessionAttributeResolvesToActiveStudy() {
        UUID id = UUID.randomUUID();
        StudySession study = new StudySession();
        study.setStartedAt(now.minusSeconds(30));
        study.setStatus(StudySessionStatus.ACTIVE);
        study.setCurrentPhase(StudyPhase.TASK_1);
        MockHttpSession http = new MockHttpSession();
        http.setAttribute(StudyTrackingService.SESSION_ATTRIBUTE, id);
        when(sessions.findById(id)).thenReturn(Optional.of(study));

        service.trackIfActive(http, StudyEventType.PLAN_ADOPTED);

        var captor = org.mockito.ArgumentCaptor.forClass(StudyEvent.class);
        verify(events).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(StudyEventType.PLAN_ADOPTED);
        assertThat(captor.getValue().getOccurredAt()).isEqualTo(now);
    }

    @Test
    void inactiveNormalSessionProducesNoEvent() {
        service.trackIfActive(new MockHttpSession(), StudyEventType.PLAN_GENERATED);
        verify(events, never()).save(any());
    }

    @Test
    void generatedPlanIsTrackedOnlyOncePerWorkflow() {
        UUID id = UUID.randomUUID();
        UUID workflowId = UUID.randomUUID();
        StudySession study = new StudySession();
        study.setStatus(StudySessionStatus.ACTIVE);
        study.setCurrentPhase(StudyPhase.TASK_1);
        MockHttpSession http = new MockHttpSession();
        http.setAttribute(StudyTrackingService.SESSION_ATTRIBUTE, id);
        when(sessions.findById(id)).thenReturn(Optional.of(study));

        service.trackGeneratedPlanIfActive(http, workflowId);
        service.trackGeneratedPlanIfActive(http, workflowId);

        verify(events, times(1)).save(any());
    }

    @Test
    void finishCompletesStudyAndClearsHttpSession() {
        UUID id = UUID.randomUUID();
        StudySession study = new StudySession();
        study.setStatus(StudySessionStatus.ACTIVE);
        study.setCurrentPhase(StudyPhase.TASK_2);
        MockHttpSession http = new MockHttpSession();
        http.setAttribute(StudyTrackingService.SESSION_ATTRIBUTE, id);
        when(sessions.findById(id)).thenReturn(Optional.of(study));

        assertThat(service.finish(http)).isTrue();
        assertThat(study.getCompletedAt()).isEqualTo(now);
        assertThat(study.getStatus()).isEqualTo(StudySessionStatus.COMPLETED);
        assertThat(http.getAttribute(StudyTrackingService.SESSION_ATTRIBUTE)).isNull();
        verify(events, times(2)).save(any());
    }

    @Test
    void startCreatesOneRandomInternalSessionAfterConsent() {
        UUID id = UUID.randomUUID();
        MockHttpSession http = new MockHttpSession();
        when(sessions.save(any(StudySession.class))).thenAnswer(invocation -> {
            StudySession saved = invocation.getArgument(0);
            saved.setId(id);
            return saved;
        });

        UUID result = service.start(http);

        assertThat(result).isEqualTo(id);
        assertThat(http.getAttribute(StudyTrackingService.SESSION_ATTRIBUTE)).isEqualTo(id);
        assertThat(http.getAttribute(StudyTrackingService.PHASE_ATTRIBUTE)).isEqualTo("TASK_1");
        var sessionCaptor = org.mockito.ArgumentCaptor.forClass(StudySession.class);
        verify(sessions, times(1)).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getConsentGivenAt()).isEqualTo(now);
        assertThat(sessionCaptor.getValue().getStartedAt()).isEqualTo(now);
        assertThat(sessionCaptor.getValue().getStatus()).isEqualTo(StudySessionStatus.ACTIVE);
        verify(events, times(1)).save(any(StudyEvent.class));
    }

    @Test
    void taskTwoKeepsSameSessionAndProjectAndEventsCarryThePhase() {
        UUID id = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        StudySession study = new StudySession();
        study.setId(id);
        study.setStatus(StudySessionStatus.ACTIVE);
        study.setProjectId(projectId);
        study.setCurrentPhase(StudyPhase.TASK_1);
        MockHttpSession http = new MockHttpSession();
        http.setAttribute(StudyTrackingService.SESSION_ATTRIBUTE, id);
        when(sessions.findById(id)).thenReturn(Optional.of(study));

        service.beginTaskTwo(http);
        service.trackIfActive(http, StudyEventType.PLAN_AI_CHANGE_STARTED);

        var captor = org.mockito.ArgumentCaptor.forClass(StudyEvent.class);
        verify(events).save(captor.capture());
        assertThat(study.getProjectId()).isEqualTo(projectId);
        assertThat(captor.getValue().getStudySession()).isSameAs(study);
        assertThat(captor.getValue().getStudyPhase()).isEqualTo(StudyPhase.TASK_2);
        assertThat(http.getAttribute(StudyTrackingService.PHASE_ATTRIBUTE)).isEqualTo("TASK_2");
    }

    @Test
    void abortKeepsSessionClearlyExcludedFromCompletedEvaluation() {
        UUID id = UUID.randomUUID();
        StudySession study = new StudySession();
        study.setStatus(StudySessionStatus.ACTIVE);
        study.setCurrentPhase(StudyPhase.TASK_1);
        MockHttpSession http = new MockHttpSession();
        http.setAttribute(StudyTrackingService.SESSION_ATTRIBUTE, id);
        when(sessions.findById(id)).thenReturn(Optional.of(study));

        assertThat(service.abort(http)).isTrue();

        assertThat(study.getStatus()).isEqualTo(StudySessionStatus.ABORTED);
        assertThat(study.getCompletedAt()).isNull();
        assertThat(http.getAttribute(StudyTrackingService.SESSION_ATTRIBUTE)).isNull();
        var captor = org.mockito.ArgumentCaptor.forClass(StudyEvent.class);
        verify(events).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(StudyEventType.STUDY_ABORTED);
    }
}
