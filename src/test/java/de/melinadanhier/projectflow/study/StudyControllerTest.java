package de.melinadanhier.projectflow.study;

import de.melinadanhier.projectflow.study.controller.StudyController;
import de.melinadanhier.projectflow.study.dto.StudyConsentForm;
import de.melinadanhier.projectflow.study.service.StudyAbortMailService;
import de.melinadanhier.projectflow.study.service.StudyEnrollmentService;
import de.melinadanhier.projectflow.study.service.StudyTrackingService;
import de.melinadanhier.projectflow.study.service.StudyUserService;
import de.melinadanhier.projectflow.user.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class StudyControllerTest {

    private final StudyTrackingService trackingService = mock(StudyTrackingService.class);
    private final StudyUserService studyUserService = mock(StudyUserService.class);
    private final StudyEnrollmentService enrollmentService = mock(StudyEnrollmentService.class);
    private final StudyAbortMailService abortMailService = mock(StudyAbortMailService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-22T20:45:00Z"), ZoneId.of("UTC"));
    private final HttpSession session = mock(HttpSession.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final User studyUser = mock(User.class);
    private final BindingResult bindingResult = mock(BindingResult.class);

    private StudyController controller;

    @BeforeEach
    void setUp() {
        controller = new StudyController(trackingService, studyUserService, enrollmentService, abortMailService, clock);
        ReflectionTestUtils.setField(controller, "questionnaireUrl", "https://survey.example/study");
    }

    @Test
    void studyRedirectsToStart() {
        assertThat(controller.studyRedirect()).isEqualTo("redirect:/study/start");
    }

    @Test
    void publicInformationPageCreatesNoStudySession() {
        ExtendedModelMap model = new ExtendedModelMap();

        assertThat(controller.information(model)).isEqualTo("study/start");
        assertThat(model).containsKey("studyConsentForm");
        verifyNoInteractions(trackingService, studyUserService, enrollmentService);
    }

    @Test
    void startWithoutConsentCreatesNoStudyData() {
        StudyConsentForm form = new StudyConsentForm();
        when(bindingResult.hasErrors()).thenReturn(true);

        assertThat(controller.start(form, bindingResult, new ExtendedModelMap(), session, request, response))
                .isEqualTo("study/start");
        verifyNoInteractions(trackingService, studyUserService, enrollmentService);
    }

    @Test
    void consentFormRejectsUncheckedConsent() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator().validate(new StudyConsentForm()))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactly("consent");
        }
    }

    @Test
    void consentedStartCreatesOneInternalSessionAndLogsInAnonymousUser() {
        StudyConsentForm form = new StudyConsentForm();
        form.setConsent(true);
        when(enrollmentService.start(session)).thenReturn(studyUser);

        String view = controller.start(
                form, bindingResult, new ExtendedModelMap(), session, request, response);

        assertThat(view).isEqualTo("redirect:/projects");
        verify(enrollmentService, times(1)).start(session);
        verify(studyUserService).login(studyUser, request, response);
    }

    @Test
    void completeTaskOneRedirectsToPlanWithoutLogout() {
        UUID projectId = UUID.randomUUID();
        when(trackingService.completeTaskOne(session)).thenReturn(projectId);

        assertThat(controller.completeTaskOne(session)).isEqualTo("redirect:/projects/" + projectId + "/plan");
        verify(trackingService).completeTaskOne(session);
        verify(session).setAttribute(StudyTrackingService.SHOW_INTRO_ATTRIBUTE, "TASK_2");
        verifyNoInteractions(studyUserService);
    }

    @Test
    void completeTaskTwoRedirectsToCompletedWithoutLogout() {
        assertThat(controller.completeTaskTwo(session)).isEqualTo("redirect:/study/completed");
        verify(trackingService).completeTaskTwo(session);
        verifyNoInteractions(studyUserService);
    }

    @Test
    void completedPageRendersViewForActiveSession() {
        ExtendedModelMap model = new ExtendedModelMap();
        when(session.getAttribute(StudyTrackingService.SESSION_ATTRIBUTE)).thenReturn(UUID.randomUUID());

        assertThat(controller.completed(session, model)).isEqualTo("study/completed");
        assertThat(model.get("studyPage")).isEqualTo(true);
    }

    @Test
    void dismissIntroRemovesSessionAttribute() {
        assertThat(controller.dismissIntro(session).getStatusCode().is2xxSuccessful()).isTrue();
        verify(session).removeAttribute(StudyTrackingService.SHOW_INTRO_ATTRIBUTE);
    }

    @Test
    void continueStudyRedirectsToCompletedWhenTasksAreFinished() {
        when(session.getAttribute(StudyTrackingService.TASKS_COMPLETED_ATTRIBUTE)).thenReturn(true);

        assertThat(controller.continueStudy(session)).isEqualTo("redirect:/study/completed");
        verifyNoInteractions(trackingService);
    }

    @Test
    void returnToQuestionnaireRedirectsToCompletedWhenTasksAreFinished() {
        when(session.getAttribute(StudyTrackingService.TASKS_COMPLETED_ATTRIBUTE)).thenReturn(true);

        assertThat(controller.returnToQuestionnaire(session)).isEqualTo("redirect:/study/completed");
        verifyNoInteractions(trackingService);
    }

    @Test
    void continueUsesOnlyCurrentInternalSession() {
        UUID projectId = UUID.randomUUID();
        when(trackingService.activeProjectId(session)).thenReturn(Optional.of(projectId));
        when(trackingService.canCompleteTaskOne(session)).thenReturn(true);
        when(trackingService.completeTaskOne(session)).thenReturn(projectId);

        assertThat(controller.continueStudy(session)).isEqualTo("redirect:/projects/" + projectId + "/plan");
        verify(trackingService).completeTaskOne(session);
        verifyNoInteractions(studyUserService);
    }

    @Test
    void questionnaireRedirectContainsNoInternalIdentifier() {
        when(trackingService.finish(session)).thenReturn(true);

        assertThat(controller.finish(session, request, response))
                .isEqualTo("redirect:https://survey.example/study");
        verify(trackingService).finish(session);
        verify(studyUserService).logout(request, response);
    }

    @Test
    void finishRedirectsGracefullyWhenNoActiveSession() {
        when(trackingService.finish(session)).thenReturn(false);

        assertThat(controller.finish(session, request, response))
                .isEqualTo("redirect:/");
        verifyNoInteractions(studyUserService);
    }

    @Test
    void explainsRestrictedStudyFunction() {
        assertThat(controller.restricted()).isEqualTo("study/restricted");
    }

    @Test
    void abortRedirectsToAbortedPageWithoutQuestionnaireRedirect() {
        when(trackingService.abort(session)).thenReturn(true);

        assertThat(controller.abort(session, request, response))
                .isEqualTo("redirect:/study/aborted");
        verify(trackingService).abort(session);
        verify(studyUserService).logout(request, response);
    }

    @Test
    void abortRedirectsGracefullyWhenNoActiveSession() {
        when(trackingService.abort(session)).thenReturn(false);

        assertThat(controller.abort(session, request, response))
                .isEqualTo("redirect:/study/aborted");
        verifyNoInteractions(studyUserService);
    }

    @Test
    void abortedPageRendersView() {
        ExtendedModelMap model = new ExtendedModelMap();

        assertThat(controller.aborted(model)).isEqualTo("study/aborted");
        assertThat(model.get("studyPage")).isEqualTo(true);
    }

    // ── Abort report tests ──

    @Test
    void abortReportCallsMailServiceWithComment() {
        var form = new de.melinadanhier.projectflow.study.dto.StudyAbortReportForm();
        form.setComment("Fehler beim Plan");
        form.setCurrentPage("/projects/123/plan");

        UUID studySessionId = UUID.randomUUID();
        when(session.getAttribute(StudyTrackingService.SESSION_ATTRIBUTE)).thenReturn(studySessionId);

        var result = controller.abortReport(form, session);

        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        verify(abortMailService).sendReport(
                eq("Fehler beim Plan"),
                eq("/projects/123/plan"),
                eq(studySessionId),
                any(Instant.class)
        );
    }

    @Test
    void abortReportAcceptsEmptyComment() {
        var form = new de.melinadanhier.projectflow.study.dto.StudyAbortReportForm();

        var result = controller.abortReport(form, session);

        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        verify(abortMailService).sendReport(
                isNull(),
                isNull(),
                isNull(),
                any(Instant.class)
        );
    }

    @Test
    void abortReportPropagatesUnexpectedExceptionsFromMailService() {
        var form = new de.melinadanhier.projectflow.study.dto.StudyAbortReportForm();
        form.setComment("test");
        doThrow(new RuntimeException("SMTP error")).when(abortMailService)
                .sendReport(any(), any(), any(), any());

        // The mail service itself catches MailException; an unexpected RuntimeException
        // would propagate. This documents that the controller doesn't swallow everything.
        assertThatThrownBy(() -> controller.abortReport(form, session))
                .isInstanceOf(RuntimeException.class);
    }
}
