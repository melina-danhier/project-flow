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
        session.setAttribute(StudyTrackingService.SESSION_ATTRIBUTE, trackingService.start(participant));
        return "redirect:/projects/new";
    }

    @GetMapping("/study/finish")
    public String finish(HttpSession session) {
        trackingService.finish(session);
        return "redirect:" + questionnaireUrl;
    }
}
