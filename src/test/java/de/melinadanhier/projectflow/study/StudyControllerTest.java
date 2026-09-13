package de.melinadanhier.projectflow.study;

import de.melinadanhier.projectflow.study.controller.StudyController;
import de.melinadanhier.projectflow.study.service.StudyTrackingService;
import de.melinadanhier.projectflow.study.service.StudyUserService;
import de.melinadanhier.projectflow.user.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudyControllerTest {

    private final StudyTrackingService trackingService = mock(StudyTrackingService.class);
    private final StudyUserService studyUserService = mock(StudyUserService.class);
    private final HttpSession session = mock(HttpSession.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final User studyUser = mock(User.class);

    private StudyController controller;

    @BeforeEach
    void setUp() {
        controller = new StudyController(trackingService, studyUserService);
    }

    @Test
    void startLogsInAutomaticallyAndRedirectsToProjectOverview() {
        when(studyUserService.getOrCreateStudyUser("P-17")).thenReturn(studyUser);

        String view = controller.start(
                "P-17", session, request, response
        );

        assertThat(view).isEqualTo("redirect:/projects");
        verify(studyUserService).login(studyUser, request, response);
        verify(trackingService).startOrResume(session, "P-17");
        verify(trackingService).beginTaskOne(session);
    }

    @Test
    void continueLogsInAutomaticallyAndRedirectsToProjectOverview() {
        when(studyUserService.getOrCreateStudyUser("P-17")).thenReturn(studyUser);
        when(trackingService.activeProjectId(session)).thenReturn(Optional.of(UUID.randomUUID()));

        String view = controller.continueStudy(
                "P-17", session, request, response
        );

        assertThat(view).isEqualTo("redirect:/projects");
        verify(studyUserService).login(studyUser, request, response);
        verify(trackingService).resume(session, "P-17");
        verify(trackingService).beginTaskTwo(session);
    }

    @Test
    void explainsRestrictedStudyFunction() {
        assertThat(controller.restricted()).isEqualTo("study/restricted");
    }
}
