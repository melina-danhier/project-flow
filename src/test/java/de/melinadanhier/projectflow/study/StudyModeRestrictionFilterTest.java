package de.melinadanhier.projectflow.study;

import de.melinadanhier.projectflow.security.filter.StudyModeRestrictionFilter;
import de.melinadanhier.projectflow.study.service.StudyTrackingService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class StudyModeRestrictionFilterTest {

    private final StudyModeRestrictionFilter filter = new StudyModeRestrictionFilter();

    @ParameterizedTest
    @MethodSource("blockedRequests")
    void blocksStudyForeignFunctions(String method, String path, String parameter, String value) throws Exception {
        MockHttpServletRequest request = activeStudyRequest(method, path);
        if (parameter != null) {
            request.addParameter(parameter, value);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getRedirectedUrl()).isEqualTo("/study/restricted");
        assertThat(chain.getRequest()).isNull();
    }

    @ParameterizedTest
    @MethodSource("allowedRequests")
    void keepsRelevantStudyFunctionsAvailable(String method, String path) throws Exception {
        MockHttpServletRequest request = activeStudyRequest(method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @org.junit.jupiter.api.Test
    void redirectsToCompletedWhenTasksAreCompleted() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/projects/123/plan");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(StudyTrackingService.SESSION_ATTRIBUTE, UUID.randomUUID());
        session.setAttribute(StudyTrackingService.TASKS_COMPLETED_ATTRIBUTE, true);
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getRedirectedUrl()).isEqualTo("/study/completed");
        assertThat(chain.getRequest()).isNull();

        // Also verify for /templates and /
        MockHttpServletRequest templateRequest = new MockHttpServletRequest("GET", "/templates");
        templateRequest.setSession(session);
        MockHttpServletResponse templateResponse = new MockHttpServletResponse();
        filter.doFilter(templateRequest, templateResponse, new MockFilterChain());
        assertThat(templateResponse.getRedirectedUrl()).isEqualTo("/study/completed");

        MockHttpServletRequest staticRequest = new MockHttpServletRequest("GET", "/css/app.css");
        staticRequest.setSession(session);
        MockHttpServletResponse staticResponse = new MockHttpServletResponse();
        MockFilterChain staticChain = new MockFilterChain();
        filter.doFilter(staticRequest, staticResponse, staticChain);
        assertThat(staticResponse.getRedirectedUrl()).isNull();
        assertThat(staticChain.getRequest()).isSameAs(staticRequest);
    }

    private MockHttpServletRequest activeStudyRequest(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(StudyTrackingService.SESSION_ATTRIBUTE, UUID.randomUUID());
        request.setSession(session);
        return request;
    }

    private static Stream<Arguments> blockedRequests() {
        return Stream.of(
                Arguments.of("GET", "/login", null, null),
                Arguments.of("POST", "/register", null, null),
                Arguments.of("POST", "/logout", null, null),
                Arguments.of("GET", "/study/start", null, null),
                Arguments.of("POST", "/projects/123/archive", null, null),
                Arguments.of("POST", "/projects/123/trash", null, null),
                Arguments.of("POST", "/projects/123/reactivate", null, null),
                Arguments.of("POST", "/projects/123/delete", null, null),
                Arguments.of("POST", "/projects/bulk/delete", null, null),
                Arguments.of("GET", "/projects/123/members", null, null),
                Arguments.of("POST", "/projects/123/members/456/remove", null, null),
                Arguments.of("GET", "/projects", "location", "TRASH"),
                Arguments.of("GET", "/projects/search", "location", "ARCHIVE"),
                Arguments.of("GET", "/projects/new", "templateId", UUID.randomUUID().toString()),
                Arguments.of("GET", "/projects/new/template", null, null),
                Arguments.of("POST", "/projects/new/template/123", null, null),
                Arguments.of("POST", "/projects/new/method", "creationType", "TEMPLATE")
        );
    }

    private static Stream<Arguments> allowedRequests() {
        return Stream.of(
                Arguments.of("GET", "/projects"),
                Arguments.of("GET", "/projects/search"),
                Arguments.of("GET", "/projects/new"),
                Arguments.of("POST", "/projects/new/method"),
                Arguments.of("GET", "/projects/new/ai/details"),
                Arguments.of("GET", "/status/123"),
                Arguments.of("GET", "/projects/123/draft/review"),
                Arguments.of("GET", "/projects/123/plan"),
                Arguments.of("GET", "/projects/123/tasks/456"),
                Arguments.of("GET", "/projects/123/plan/ai-change"),
                Arguments.of("GET", "/projects/123/plan-elements/TASK/456/improve"),
                Arguments.of("GET", "/study/return")
        );
    }
}
