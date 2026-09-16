package de.melinadanhier.projectflow.study;

import de.melinadanhier.projectflow.study.controller.StudyController;
import de.melinadanhier.projectflow.study.dto.StudyConsentForm;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class StudyControllerTest {

    private final StudyTrackingService trackingService = mock(StudyTrackingService.class);
    private final StudyUserService studyUserService = mock(StudyUserService.class);
    private final StudyEnrollmentService enrollmentService = mock(StudyEnrollmentService.class);
    private final HttpSession session = mock(HttpSession.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final User studyUser = mock(User.class);
    private final BindingResult bindingResult = mock(BindingResult.class);

    private StudyController controller;

    @BeforeEach
    void setUp() {
        controller = new StudyController(trackingService, studyUserService, enrollmentService);
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
    void continueUsesOnlyCurrentInternalSession() {
        when(trackingService.activeProjectId(session)).thenReturn(Optional.of(UUID.randomUUID()));

        assertThat(controller.continueStudy(session)).isEqualTo("redirect:/projects");
        verify(trackingService).beginTaskTwo(session);
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
    void explainsRestrictedStudyFunction() {
        assertThat(controller.restricted()).isEqualTo("study/restricted");
    }
}
