package de.melinadanhier.projectflow.study.controller;

import de.melinadanhier.projectflow.study.dto.StudyConsentForm;
import de.melinadanhier.projectflow.study.service.StudyEnrollmentService;
import de.melinadanhier.projectflow.study.service.StudyTrackingService;
import de.melinadanhier.projectflow.study.service.StudyUserService;
import de.melinadanhier.projectflow.user.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class StudyController {
    private final StudyTrackingService trackingService;
    private final StudyUserService studyUserService;
    private final StudyEnrollmentService enrollmentService;

    @Value("${projectflow.study.questionnaire-url:}")
    private String questionnaireUrl;

    @GetMapping("/study")
    public String studyRedirect() {
        return "redirect:/study/start";
    }

    @GetMapping("/study/start")
    public String information(Model model) {
        model.addAttribute("studyPage", true);
        if (!model.containsAttribute("studyConsentForm")) {
            model.addAttribute("studyConsentForm", new StudyConsentForm());
        }
        return "study/start";
    }

    @PostMapping("/study/start")
    public String start(
            @Valid @ModelAttribute("studyConsentForm") StudyConsentForm form,
            BindingResult bindingResult,
            Model model,
            HttpSession session,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("studyPage", true);
            return "study/start";
        }

        User studyUser = enrollmentService.start(session);
        studyUserService.login(studyUser, request, response);

        return "redirect:/projects";
    }

    @GetMapping("/study/return")
    public String returnToQuestionnaire(HttpSession session) {
        trackingService.completeCurrentTask(session);
        return questionnaireRedirect();
    }

    @GetMapping("/study/continue")
    public String continueStudy(HttpSession session) {
        trackingService.activeProjectId(session)
                .orElseThrow(() -> new IllegalStateException("Kein Studienprojekt vorhanden."));
        trackingService.beginTaskTwo(session);
        return "redirect:/projects";
    }

    @PostMapping("/study/finish")
    public String finish(
            HttpSession session,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (!trackingService.finish(session)) {
            throw new IllegalStateException("Keine aktive Studien-Session vorhanden.");
        }
        studyUserService.logout(request, response);
        return questionnaireRedirect();
    }

    @PostMapping("/study/abort")
    public String abort(
            HttpSession session,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (!trackingService.abort(session)) {
            throw new IllegalStateException("Keine aktive Studien-Session vorhanden.");
        }
        studyUserService.logout(request, response);
        return questionnaireRedirect();
    }

    @GetMapping("/study/restricted")
    public String restricted() {
        return "study/restricted";
    }

    private String questionnaireRedirect() {
        if (questionnaireUrl == null || questionnaireUrl.isBlank()) {
            throw new IllegalStateException("Die URL der Studienbefragung ist nicht konfiguriert.");
        }
        return "redirect:" + questionnaireUrl;
    }
}
