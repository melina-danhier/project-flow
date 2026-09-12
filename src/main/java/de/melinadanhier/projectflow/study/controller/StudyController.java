package de.melinadanhier.projectflow.study.controller;

import de.melinadanhier.projectflow.study.service.StudyTrackingService;
import de.melinadanhier.projectflow.study.service.StudyUserService;
import de.melinadanhier.projectflow.user.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Controller
@Validated
@RequiredArgsConstructor
public class StudyController {
    private final StudyTrackingService trackingService;
    private final StudyUserService studyUserService;

    @Value("${projectflow.study.questionnaire-url:}")
    private String questionnaireUrl;

    @GetMapping("/study/start")
    public String start(
            @RequestParam("participant")
            @Pattern(regexp = "[A-Za-z0-9_-]{1,64}", message = "Ungültiger Studiencode.")
            String participant,
            HttpSession session,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        User studyUser = studyUserService.getOrCreateStudyUser(participant);
        studyUserService.login(studyUser, request, response);

        trackingService.startOrResume(session, participant);
        trackingService.beginTaskOne(session);

        var projectId = trackingService.activeProjectId(session);

        return projectId
                .map(uuid -> "redirect:/projects/" + uuid + "/plan")
                .orElse("redirect:/projects/new");
    }

    @GetMapping("/study/return")
    public String returnToQuestionnaire(HttpSession session) {
        String participantId = trackingService.activeParticipantId(session)
                .orElseThrow(() -> new IllegalStateException("Keine aktive Studien-Session vorhanden."));
        return questionnaireRedirect(participantId);
    }

    @GetMapping("/study/continue")
    public String continueStudy(
            @RequestParam("participant")
            @Pattern(regexp = "[A-Za-z0-9_-]{1,64}", message = "Ungültiger Studiencode.")
            String participant,
            HttpSession session,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        User studyUser = studyUserService.getOrCreateStudyUser(participant);
        studyUserService.login(studyUser, request, response);

        trackingService.resume(session, participant);

        UUID projectId = trackingService.activeProjectId(session)
                .orElseThrow(() -> new IllegalStateException("Kein Studienprojekt vorhanden."));

        trackingService.beginTaskTwo(session);

        return "redirect:/projects/" + projectId + "/plan";
    }

    @GetMapping("/study/finish")
    public String finish(HttpSession session) {
        String participantId = trackingService.activeParticipantId(session)
                .orElseThrow(() -> new IllegalStateException("Keine aktive Studien-Session vorhanden."));
        trackingService.finish(session);
        return questionnaireRedirect(participantId);
    }

    private String questionnaireRedirect(String participantId) {
        if (questionnaireUrl == null || questionnaireUrl.isBlank()) {
            throw new IllegalStateException("Die URL der Studienbefragung ist nicht konfiguriert.");
        }
        String separator = questionnaireUrl.contains("?") ? "&" : "?";

        return "redirect:"
                + questionnaireUrl
                + separator
                + "i="
                + URLEncoder.encode(participantId, StandardCharsets.UTF_8);
    }
}
