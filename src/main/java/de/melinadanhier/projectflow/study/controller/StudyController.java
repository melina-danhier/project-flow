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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

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
        session.setAttribute(StudyTrackingService.SHOW_INTRO_ATTRIBUTE, "TASK_1");

        return "redirect:/projects";
    }

    @PostMapping("/study/intro/dismiss")
    @ResponseBody
    public ResponseEntity<Void> dismissIntro(HttpSession session) {
        if (session != null) {
            session.removeAttribute(StudyTrackingService.SHOW_INTRO_ATTRIBUTE);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/study/task-1/complete")
    public String completeTaskOne(HttpSession session) {
        UUID projectId = trackingService.completeTaskOne(session);
        session.setAttribute(StudyTrackingService.SHOW_INTRO_ATTRIBUTE, "TASK_2");
        return "redirect:/projects/" + projectId + "/plan";
    }

    @PostMapping("/study/task-2/complete")
    public String completeTaskTwo(HttpSession session) {
        trackingService.completeTaskTwo(session);
        return "redirect:/study/completed";
    }

    @GetMapping("/study/completed")
    public String completed(HttpSession session, Model model) {
        if (session == null || session.getAttribute(StudyTrackingService.SESSION_ATTRIBUTE) == null) {
            return "redirect:/";
        }
        model.addAttribute("studyPage", true);
        return "study/completed";
    }

    @GetMapping("/study/continue")
    public String continueStudy(HttpSession session) {
        if (session != null && session.getAttribute(StudyTrackingService.TASKS_COMPLETED_ATTRIBUTE) != null) {
            return "redirect:/study/completed";
        }
        UUID projectId = trackingService.activeProjectId(session)
                .orElseThrow(() -> new IllegalStateException("Kein Studienprojekt vorhanden."));
        if (trackingService.canCompleteTaskOne(session)) {
            trackingService.completeTaskOne(session);
        } else {
            trackingService.beginTaskTwo(session);
        }
        return "redirect:/projects/" + projectId + "/plan";
    }

    @GetMapping("/study/return")
    public String returnToQuestionnaire(HttpSession session) {
        if (session != null && session.getAttribute(StudyTrackingService.TASKS_COMPLETED_ATTRIBUTE) != null) {
            return "redirect:/study/completed";
        }
        if (trackingService.canCompleteTaskOne(session)) {
            UUID projectId = trackingService.completeTaskOne(session);
            return "redirect:/projects/" + projectId + "/plan";
        }
        trackingService.completeCurrentTask(session);
        return questionnaireRedirect();
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
