package de.melinadanhier.projectflow.security.filter;

import de.melinadanhier.projectflow.study.service.StudyTrackingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

public class StudyModeRestrictionFilter extends OncePerRequestFilter {

    private static final Pattern PROJECT_LIFECYCLE_ACTION = Pattern.compile(
            "^/projects/[^/]+/(archive|trash|reactivate|delete|pin|unpin)$"
    );

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean activeStudy = session != null
                && session.getAttribute(StudyTrackingService.SESSION_ATTRIBUTE) != null;

        if (activeStudy) {
            boolean tasksCompleted = session.getAttribute(StudyTrackingService.TASKS_COMPLETED_ATTRIBUTE) != null;
            if (tasksCompleted && path.startsWith("/projects")) {
                response.sendRedirect(request.getContextPath() + "/study/completed");
                return;
            }
            if (isBlocked(request, path)) {
                response.sendRedirect(request.getContextPath() + "/study/restricted");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isBlocked(HttpServletRequest request, String path) {
        if ("/login".equals(path) || "/register".equals(path) || "/logout".equals(path)
                || "/study/start".equals(path)) {
            return true;
        }
        if (path.startsWith("/projects/") && path.contains("/members")) {
            return true;
        }
        if (PROJECT_LIFECYCLE_ACTION.matcher(path).matches() || path.startsWith("/projects/bulk/")) {
            return true;
        }
        if (path.startsWith("/projects/new/template")) {
            return true;
        }
        if ("/projects/new".equals(path) && request.getParameter("templateId") != null) {
            return true;
        }
        if ("/projects/new/method".equals(path)
                && "POST".equalsIgnoreCase(request.getMethod())
                && "TEMPLATE".equals(request.getParameter("creationType"))) {
            return true;
        }
        if ("/projects".equals(path) || "/projects/search".equals(path)) {
            String location = request.getParameter("location");
            return "ARCHIVE".equals(location) || "TRASH".equals(location);
        }
        return false;
    }
}
