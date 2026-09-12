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
        MockHttpSession http = new MockHttpSession();
        http.setAttribute(StudyTrackingService.SESSION_ATTRIBUTE, id);
        when(sessions.findById(id)).thenReturn(Optional.of(study));

        assertThat(service.finish(http)).isTrue();
        assertThat(study.getCompletedAt()).isEqualTo(now);
        assertThat(http.getAttribute(StudyTrackingService.SESSION_ATTRIBUTE)).isNull();
    }

    @Test
    void startResumesOpenSessionAndRestoresItsPersistedPhase() {
        UUID id = UUID.randomUUID();
        StudySession study = new StudySession();
        study.setId(id);
        study.setParticipantId("P-17");
        study.setStartedAt(now.minusSeconds(60));
        study.setCurrentPhase(StudyPhase.TASK_2);
        MockHttpSession http = new MockHttpSession();
        when(sessions.findFirstByParticipantIdAndCompletedAtIsNullOrderByStartedAtDesc("P-17"))
                .thenReturn(Optional.of(study));
        when(sessions.findById(id)).thenReturn(Optional.of(study));

        service.startOrResume(http, "P-17");
        service.beginTaskOne(http);

        assertThat(http.getAttribute(StudyTrackingService.SESSION_ATTRIBUTE)).isEqualTo(id);
        assertThat(http.getAttribute(StudyTrackingService.PHASE_ATTRIBUTE)).isEqualTo("TASK_2");
        assertThat(study.getCurrentPhase()).isEqualTo(StudyPhase.TASK_2);
        verify(sessions, never()).save(any());
    }

    @Test
    void taskTwoKeepsSameSessionAndProjectAndEventsCarryThePhase() {
        UUID id = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        StudySession study = new StudySession();
        study.setId(id);
        study.setProjectId(projectId);
        study.setCurrentPhase(StudyPhase.TASK_1);
        MockHttpSession http = new MockHttpSession();
        http.setAttribute(StudyTrackingService.SESSION_ATTRIBUTE, id);
        when(sessions.findById(id)).thenReturn(Optional.of(study));

        service.beginTaskTwo(http);
        service.trackIfActive(http, StudyEventType.AI_EDIT_STARTED);

        var captor = org.mockito.ArgumentCaptor.forClass(StudyEvent.class);
        verify(events).save(captor.capture());
        assertThat(study.getProjectId()).isEqualTo(projectId);
        assertThat(captor.getValue().getStudySession()).isSameAs(study);
        assertThat(captor.getValue().getStudyPhase()).isEqualTo(StudyPhase.TASK_2);
        assertThat(http.getAttribute(StudyTrackingService.PHASE_ATTRIBUTE)).isEqualTo("TASK_2");
    }
}
