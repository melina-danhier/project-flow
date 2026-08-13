package com.melina.projectflow.plancontainer.project.controller;

import com.melina.projectflow.plancontainer.project.service.ProjectService;
import com.melina.projectflow.security.model.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping("/projects")
    public String projectOverview(@AuthenticationPrincipal AuthenticatedUser currentUser, Model model) {
        model.addAttribute("projects", projectService.findAccessibleProjects(currentUser.userId()));
        return "projects/overview";
    }
}
