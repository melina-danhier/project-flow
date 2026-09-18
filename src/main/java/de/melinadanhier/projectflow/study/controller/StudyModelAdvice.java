package de.melinadanhier.projectflow.study.controller;

import de.melinadanhier.projectflow.study.service.StudyTrackingService;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class StudyModelAdvice {

    private final StudyTrackingService studyTrackingService;

    public StudyModelAdvice(StudyTrackingService studyTrackingService) {
        this.studyTrackingService = studyTrackingService;
    }

    @ModelAttribute
    public void addStudyAttributes(HttpSession session, Model model) {
        if (session != null && "TASK_2".equals(session.getAttribute(StudyTrackingService.PHASE_ATTRIBUTE))) {
            boolean canComplete = studyTrackingService.canCompleteTaskTwo(session);
            model.addAttribute("studyTaskTwoCanComplete", canComplete);
        }
    }
}
