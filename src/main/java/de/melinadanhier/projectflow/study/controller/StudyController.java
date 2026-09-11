package de.melinadanhier.projectflow.study.controller;

import de.melinadanhier.projectflow.study.service.StudyTrackingService;
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

    @Value("${projectflow.study.questionnaire-url:/projects}")
    private String questionnaireUrl;

    @GetMapping("/study/start")
    public String start(@RequestParam("participant")
                        @Pattern(regexp = "[A-Za-z0-9_-]{1,64}", message = "Ungültiger Studiencode.")
                        String participant, HttpSession session) {
        trackingService.finish(session);
        session.setAttribute(
                StudyTrackingService.SESSION_ATTRIBUTE,
                trackingService.start(participant));
        session.setAttribute(
                StudyTrackingService.PHASE_ATTRIBUTE,
                StudyTrackingService.PHASE_TASK_1
        );
        return "redirect:/projects/new";
    }

    @GetMapping("/study/return")
    public String returnToQuestionnaire(HttpSession session) {
        String participantId = trackingService.activeParticipantId(session)
                .orElseThrow(() ->
                        new IllegalStateException("Keine aktive Studien-Session vorhanden."));

        return questionnaireRedirect(participantId);
    }

    @GetMapping("/study/continue")
    public String continueStudy(HttpSession session) {
        UUID projectId = trackingService.activeProjectId(session)
                .orElseThrow(() ->
                        new IllegalStateException("Kein Studienprojekt vorhanden."));

        session.setAttribute(
                StudyTrackingService.PHASE_ATTRIBUTE,
                StudyTrackingService.PHASE_TASK_2
        );

        return "redirect:/projects/" + projectId + "/plan";
    }

    @GetMapping("/study/finish")
    public String finish(HttpSession session) {
        String participantId = trackingService.activeParticipantId(session)
                .orElseThrow(() ->
                        new IllegalStateException("Keine aktive Studien-Session vorhanden."));

        trackingService.finish(session);

        return questionnaireRedirect(participantId);
    }

    private String questionnaireRedirect(String participantId) {
        String separator = questionnaireUrl.contains("?") ? "&" : "?";

        return "redirect:"
                + questionnaireUrl
                + separator
                + "i="
                + URLEncoder.encode(participantId, StandardCharsets.UTF_8);
    }
}
